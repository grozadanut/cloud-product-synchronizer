package ro.linic.cloud.service;

import java.net.URI;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.client.Traverson;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.java.Log;
import ro.linic.cloud.command.ChangeNameCommand;
import ro.linic.cloud.command.ChangePriceCommand;
import ro.linic.cloud.command.ChangeStockCommand;
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
	@Autowired private WoocommerceService woocommerceService;
	@Autowired private SyncProductRepository syncProductRepo;
	@Autowired private SyncConnectionRepository syncConnRepo;
	
	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public String createConnection(final SyncConnection syncConnection) {
		if (syncConnRepo.findByCompanyId(syncConnection.getCompanyId()).isPresent())
			throw new RuntimeException("SyncConnection already exists for company id: "+syncConnection.getCompanyId());
		
		final SyncConnection savedConn = syncConnRepo.save(syncConnection);
		final List<Product> wooProducts = woocommerceService.allProducts(savedConn);
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
					woocommerceService.deactivateProduct(syncConnection, wooProd.getId());
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
				woocommerceService.putProduct(syncConnection, wooProd);
			
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
		try {
			return woocommerceService.createProduct(command);
		} catch (final Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
		
		return null;
	}

	@Override
	public ResponseEntity<Product> updatePrice(final ChangePriceCommand changePriceCommand) {
		try {
			return woocommerceService.updatePrice(changePriceCommand);
		} catch (final Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
		
		return null;
	}
	
	@Override
	public ResponseEntity<Product> updateStock(final ChangeStockCommand changeStockCommand) {
		try {
			return woocommerceService.updateStock(changeStockCommand);
		} catch (final Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
		
		return null;
	}
	
	@Override
	public ResponseEntity<Product> updateName(final ChangeNameCommand command) {
		try {
			return woocommerceService.updateName(command);
		} catch (final Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
		
		return null;
	}
	
	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public ResponseEntity<Product> deleteProduct(final DeleteProductCommand command) {
		try {
			return woocommerceService.deleteProduct(command);
		} catch (final Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
		
		return null;
	}

	protected Traverson createTraverson(final String url) {
		return new Traverson(URI.create(url), MediaTypes.HAL_JSON);
	}
}
