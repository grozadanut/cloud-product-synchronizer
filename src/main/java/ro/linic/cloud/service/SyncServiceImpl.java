package ro.linic.cloud.service;

import java.net.URI;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.client.Traverson;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
class SyncServiceImpl implements SyncService {

	@Autowired private WoocommerceApi woocommerceApi;
	@Autowired private SyncConnectionRepository syncConnRepo;
	@Autowired private SyncProductRepository syncProductRepo;
	@Autowired private RestTemplate restTemplate;
	
	@Value("${cloud.notification.service.url:http://localhost}")
	private String notificationServiceUrl;
	
	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public String createConnection(final SyncConnection syncConnection) {
		if (syncConnRepo.findByCompanyId(syncConnection.getCompanyId()).isPresent())
			throw new RuntimeException("SyncConnection already exists for company id: "+syncConnection.getCompanyId());
		
		final SyncConnection savedConn = syncConnRepo.save(syncConnection);
		final List<Product> wooProducts = woocommerceApi.allProducts(savedConn);
		final int companyId = savedConn.getCompanyId();
		
		final StringBuilder sb = new StringBuilder();
		final AtomicInteger deactivated = new AtomicInteger(0);
		final AtomicInteger nameDifference = new AtomicInteger(0);
		final AtomicInteger priceChange = new AtomicInteger(0);
		final AtomicInteger stockChange = new AtomicInteger(0);
		
		final Traverson client = createTraverson(syncConnection.getInventoryServiceUrl());
		wooProducts.forEach(wooProd ->
		{
			final CollectionModel<Product> foundLProduct = client
					.follow("products", "search", "findByCompanyIdAndBarcode")
					.withTemplateParameters(Map.of("companyId", companyId, "barcode", wooProd.getBarcode()))
					.toObject(new ParameterizedTypeReference<CollectionModel<Product>>(){});
			final Optional<Product> lProdO = foundLProduct.getContent().stream().findFirst().or(() ->
			{
				// alternate search for name
				return client.follow("products", "search", "findByCompanyIdAndNameIgnoreCase")
				.withTemplateParameters(Map.of("companyId", companyId, "name", wooProd.getName()))
				.toObject(new ParameterizedTypeReference<CollectionModel<Product>>(){})
				.getContent().stream().findFirst();
			});
			
			if (lProdO.isEmpty())
			{
				if (wooProd.getVisible())
				{
					woocommerceApi.deactivateProduct(syncConnection, wooProd.getId());
					sb.append(System.lineSeparator())
					.append(MessageFormat.format("Deactivating Woo SKU {0}: {1}", wooProd.getBarcode(), wooProd.getName(),
							deactivated.incrementAndGet()));
				}
				return;
			}
			// woo barcode exists in Linic platform
			final Product lProd = lProdO.get();
			
			boolean triggerUpdate = false;
			// update woo barcode(in case product was found by name matching)
			if (!wooProd.getBarcode().equalsIgnoreCase(lProd.getBarcode()))
			{
				triggerUpdate = true;
				wooProd.setBarcode(lProd.getBarcode());
			}
			
			// update woo name
			if (!wooProd.getName().equalsIgnoreCase(lProd.getName()))
				sb.append(System.lineSeparator())
				.append(MessageFormat.format("{0} Woo name({1}) != {2}", lProd.getBarcode(), wooProd.getName(), lProd.getName(),
						nameDifference.incrementAndGet()));
			
			// update woo price
			if (wooProd.getPricePerUom().compareTo(lProd.getPricePerUom()) != 0)
			{
				sb.append(System.lineSeparator())
				.append(MessageFormat.format("Set price of {0} {1} from {2} to {3}", wooProd.getBarcode(), wooProd.getName(),
						wooProd.getPricePerUom(), lProd.getPricePerUom(), priceChange.incrementAndGet()));
				triggerUpdate = true;
				wooProd.setPricePerUom(lProd.getPricePerUom());
			}
			
			// update woo stock
			if (wooProd.getStock().compareTo(lProd.getStock()) != 0)
			{
				sb.append(System.lineSeparator())
				.append(MessageFormat.format("Set stock of {0} {1} from {2} to {3}", wooProd.getBarcode(), wooProd.getName(),
						wooProd.getStock(), lProd.getStock(), stockChange.incrementAndGet()));
				triggerUpdate = true;
				wooProd.setStock(lProd.getStock());
			}
			
			// update WOO
			if (triggerUpdate)
				woocommerceApi.putProduct(syncConnection, wooProd);
			
			// create sync connections
			final List<SyncLine> foundSyncLine = syncProductRepo.findBySyncConnectionIdAndProductIdAndWooId(savedConn.getId(),
					lProd.getId(), wooProd.getId());
			
			if (!foundSyncLine.isEmpty())
				return;
			final SyncLine syncLine = new SyncLine(null, savedConn, lProd.getId(), wooProd.getId(), null);
			if (!wooProd.getName().equalsIgnoreCase(lProd.getName()))
				syncLine.setWooName(wooProd.getName());
			syncProductRepo.save(syncLine);
		});
		
		sb.append(System.lineSeparator()).append(System.lineSeparator())
		.append(MessageFormat.format("Woocommerce products deactivated {2}/{0}{1}"
				+ "Name differences {3}/{0}{1}"
				+ "Woocommerce price updated {4}/{0}{1}"
				+ "Woocommerce stock updated {5}/{0}{1}",
				wooProducts.size(), System.lineSeparator(),
				deactivated.get(), nameDifference.get(), priceChange.get(), stockChange.get()));
		return sb.toString();
	}
	
	@Override
	public Object updateConnection(final Integer syncConnectionId, final String inventoryServiceUrl, final String websiteUrl,
			final String key, final String secret) {
		final Optional<SyncConnection> sync = syncConnRepo.findById(syncConnectionId);
		return sync.map(s ->
		{
			s.setInventoryServiceUrl(inventoryServiceUrl);
			s.setWebsiteUrl(websiteUrl);
			s.setWebsiteKey(key);
			s.setWebsiteSecret(secret);
			return syncConnRepo.save(s);
		}).orElse(null);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void deleteConnection(final Integer syncConnectionId) {
		syncProductRepo.deleteAll(syncProductRepo.findBySyncConnectionId(syncConnectionId));
		syncConnRepo.deleteById(syncConnectionId);
	}
	
	@Override
	public Iterable<SyncConnection> findAll() {
		return syncConnRepo.findAll();
	}
	
	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public ResponseEntity<Product> createProduct(final CreateProductCommand command) {
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(command.getCompanyId());
		
		if (syncConnection.isEmpty())
			return new ResponseEntity<>(HttpStatus.OK);
		
		final Optional<SyncLine> foundSyncLine = syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(), command.getProductId());
		if (foundSyncLine.isPresent())
			throw new RuntimeException("Sync product line already exists: "+foundSyncLine.get());

		final ResponseEntity<Product> wooReponse = woocommerceApi.createProduct(syncConnection.get(),
				new Product(null, null, command.getBarcode(), command.getName(), command.getUom(), command.getPricePerUom(), null, null));

		if (!wooReponse.getStatusCode().is2xxSuccessful())
			throw new RuntimeException("Woocommerce status code "+wooReponse.getStatusCode());

		final Product wooProduct = wooReponse.getBody();
		final SyncLine syncLine = new SyncLine(null, syncConnection.get(), command.getProductId(), wooProduct.getId(), null);
		syncProductRepo.save(syncLine);
		notifyProductCreate(syncConnection.get(), wooProduct);
		return new ResponseEntity<>(wooProduct, HttpStatus.OK);
	}

	@Override
	public ResponseEntity<Product> updatePrice(final ChangePriceCommand changePriceCommand) {
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
	
	@Override
	public ResponseEntity<Product> updateStock(final ChangeStockCommand changeStockCommand) {
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
	@Transactional(propagation = Propagation.REQUIRED)
	public ResponseEntity<Product> deleteProduct(final DeleteProductCommand command) {
		if (!productIsSynced(command.getCompanyId(), command.getProductId()))
			return new ResponseEntity<>(HttpStatus.OK);
		
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(command.getCompanyId());
		final Optional<SyncLine> syncProduct = syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(),
				command.getProductId());
		syncProductRepo.delete(syncProduct.get());
		return woocommerceApi.deactivateProduct(syncConnection.get(), syncProduct.get().getWooId());
	}
	
	@Override
	public ResponseEntity<Product> updateName(final ChangeNameCommand command) {
		if (!productIsSynced(command.getCompanyId(), command.getProductId()))
			return new ResponseEntity<>(HttpStatus.OK);
		
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(command.getCompanyId());
		final Optional<SyncLine> syncLine = syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(), command.getProductId());
		notifyNameChange(syncConnection.get(), syncLine.get(), command);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	private boolean productIsSynced(final Integer companyId, final Integer productId) {
		final Optional<SyncConnection> syncConnection = syncConnRepo.findByCompanyId(companyId);
		
		if (syncConnection.isEmpty())
			return false;
		
		return syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.get().getId(), productId)
				.isPresent();
	}
	
	protected Traverson createTraverson(final String url)
	{
		return new Traverson(URI.create(url), MediaTypes.HAL_JSON);
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
}
