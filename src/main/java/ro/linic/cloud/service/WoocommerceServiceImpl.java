package ro.linic.cloud.service;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.java.Log;
import ro.linic.cloud.command.ChangeNameCommand;
import ro.linic.cloud.command.ChangePriceCommand;
import ro.linic.cloud.command.ChangeStockCommand;
import ro.linic.cloud.command.CreateNotificationCommand;
import ro.linic.cloud.command.CreateProductCommand;
import ro.linic.cloud.command.DeleteProductCommand;
import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.entity.SyncLine;
import ro.linic.cloud.pojo.Product;
import ro.linic.cloud.repository.SyncConnectionRepository;
import ro.linic.cloud.repository.SyncProductRepository;

@Log
@Service
public class WoocommerceServiceImpl implements WoocommerceService {
	@Value("${cloud.notification.service.url:http://localhost}")
	private String notificationServiceUrl;
	
	@Autowired private RestTemplate restTemplate;
	@Autowired private WoocommerceApi woocommerceApi;
	@Autowired private SyncProductRepository syncProductRepo;
	@Autowired private SyncConnectionRepository syncConnRepo;
	
	@Override
	public ResponseEntity<Product> updatePrice(ChangePriceCommand changePriceCommand) {
		if (!productIsSynced(changePriceCommand.getCompanyId(), changePriceCommand.getProductId()))
			return new ResponseEntity<>(HttpStatus.OK);
		
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(changePriceCommand.getCompanyId());
		final Optional<SyncLine> syncLine = syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(),
				changePriceCommand.getProductId());
		final Product wooProduct = Product.builder()
				.id(syncLine.get().getWooId())
				.pricePerUom(changePriceCommand.getPricePerUom())
				.build();
		return woocommerceApi.patchProduct(syncConnection.get(), wooProduct);
	}
	
	private boolean productIsSynced(final Integer companyId, final Integer productId) {
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(companyId);
		
		if (syncConnection.isEmpty())
			return false;
		
		return syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(), productId)
				.isPresent();
	}

	@Override
	public ResponseEntity<Product> updateStock(ChangeStockCommand changeStockCommand) {
		if (!productIsSynced(changeStockCommand.getCompanyId(), changeStockCommand.getProductId()))
			return new ResponseEntity<>(HttpStatus.OK);
		
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(changeStockCommand.getCompanyId());
		final Optional<SyncLine> syncProduct = syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(),
				changeStockCommand.getProductId());
		final Product wooProduct = Product.builder()
				.id(syncProduct.get().getWooId())
				.stock(changeStockCommand.getStock())
				.build();
		return woocommerceApi.patchProduct(syncConnection.get(), wooProduct);
	}

	@Override
	public ResponseEntity<Product> createProduct(CreateProductCommand command) {
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(command.getCompanyId());
		
		if (syncConnection.isEmpty())
			return new ResponseEntity<>(HttpStatus.OK);
		
		final Optional<SyncLine> foundSyncLine = syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(), command.getProductId());
		if (foundSyncLine.isPresent())
			throw new RuntimeException("Sync product line already exists: "+foundSyncLine.get());

		final ResponseEntity<Product> wooReponse = woocommerceApi.createProduct(syncConnection.get(),
				new Product(null, null, command.getBarcode(), command.getName(), command.getUom(), command.getPricePerUom(), null, null));

		if (!wooReponse.getStatusCode().is2xxSuccessful())
			return new ResponseEntity<>(null, HttpStatus.ACCEPTED);
//			throw new RuntimeException("Woocommerce status code "+wooReponse.getStatusCode());

		final Product wooProduct = wooReponse.getBody();
		final SyncLine syncLine = new SyncLine(null, syncConnection.get(), command.getProductId(), wooProduct.getId(), null);
		syncProductRepo.save(syncLine);
//		notifyProductCreate(syncConnection.get(), wooProduct);
		return new ResponseEntity<>(wooProduct, HttpStatus.OK);
	}
	
	private void notifyProductCreate(final SyncConnection connection,  final Product wooProduct) {
		final StringBuilder sb = new StringBuilder();
		
		sb.append("Produs adaugat pe woocommerce").append(System.lineSeparator())
		.append("ID: "+wooProduct.getId()).append(System.lineSeparator())
		.append("Cod: "+wooProduct.getBarcode()).append(System.lineSeparator())
		.append("Nume: "+wooProduct.getName()).append(System.lineSeparator())
		.append("Pret: "+wooProduct.getPricePerUom()).append(System.lineSeparator())
		.append(MessageFormat.format("Acceseaza {0} pentru a edita", connection.getWebsiteUrl()));
		
		restTemplate.exchange(notificationServiceUrl+"/notification", HttpMethod.POST,
				new HttpEntity<CreateNotificationCommand>(new CreateNotificationCommand(connection.getCompanyId(), sb.toString())),
				Void.class);
	}

	@Override
	public ResponseEntity<Product> deleteProduct(DeleteProductCommand command) {
		if (!productIsSynced(command.getCompanyId(), command.getProductId()))
			return new ResponseEntity<>(HttpStatus.OK);
		
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(command.getCompanyId());
		final Optional<SyncLine> syncProduct = syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(),
				command.getProductId());
		syncProductRepo.delete(syncProduct.get());
		return woocommerceApi.deactivateProduct(syncConnection.get(), syncProduct.get().getWooId());
	}

	@Override
	public ResponseEntity<Product> updateName(ChangeNameCommand command) {
		if (!productIsSynced(command.getCompanyId(), command.getProductId()))
			return new ResponseEntity<>(HttpStatus.OK);
		
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(command.getCompanyId());
		final Optional<SyncLine> syncLine = syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(), command.getProductId());
		notifyNameChange(syncConnection.get(), syncLine.get(), command);
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	private void notifyNameChange(final SyncConnection connection, final SyncLine syncLine, final ChangeNameCommand command) {
		final StringBuilder sb = new StringBuilder();
		
		sb.append("Numele produsului a fost modificat").append(System.lineSeparator())
		.append("ID: "+command.getProductId()).append(System.lineSeparator())
		.append("Woo ID: "+syncLine.getWooId()).append(System.lineSeparator())
		.append("Cod: "+command.getBarcode()).append(System.lineSeparator())
		.append("Nume nou: "+command.getName()).append(System.lineSeparator())
		.append(MessageFormat.format("Acceseaza {0} pentru a edita", connection.getWebsiteUrl()));
		
		restTemplate.exchange(notificationServiceUrl+"/notification", HttpMethod.POST,
				new HttpEntity<CreateNotificationCommand>(new CreateNotificationCommand(connection.getCompanyId(), sb.toString())),
				Void.class);
	}

	@Override
	public List<Product> allProducts(SyncConnection syncConnection) {
		return woocommerceApi.allProducts(syncConnection);
	}

	@Override
	public ResponseEntity<Product> deactivateProduct(SyncConnection syncConnection, int wooId) {
		return woocommerceApi.deactivateProduct(syncConnection, wooId);
	}
	
	@Override
	public ResponseEntity<Product> putProduct(SyncConnection syncConnection, Product wooProduct) {
		return woocommerceApi.putProduct(syncConnection, wooProduct);
	}
}
