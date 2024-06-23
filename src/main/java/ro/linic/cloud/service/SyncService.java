package ro.linic.cloud.service;

import org.springframework.http.ResponseEntity;

import ro.linic.cloud.command.ChangeNameCommand;
import ro.linic.cloud.command.ChangePriceCommand;
import ro.linic.cloud.command.ChangeStockCommand;
import ro.linic.cloud.command.CreateProductCommand;
import ro.linic.cloud.command.DeleteProductCommand;
import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.pojo.Product;

public interface SyncService {

	Object createConnection(SyncConnection syncConnection);
	void deleteConnection(Integer syncConnectionId);
	Iterable<SyncConnection> findAll();
	ResponseEntity<Product> updatePrice(ChangePriceCommand command);
	ResponseEntity<Product> updateStock(ChangeStockCommand command);
	Object updateConnection(Integer syncConnectionId, String inventoryServiceUrl, String websiteUrl, String key,
			String secret);
	ResponseEntity<Product> createProduct(CreateProductCommand command);
	ResponseEntity<Product> deleteProduct(DeleteProductCommand command);
	ResponseEntity<Product> updateName(ChangeNameCommand command);
}
