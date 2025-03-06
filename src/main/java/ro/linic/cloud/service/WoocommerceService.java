package ro.linic.cloud.service;

import java.util.List;

import org.springframework.http.ResponseEntity;

import ro.linic.cloud.command.ChangeNameCommand;
import ro.linic.cloud.command.ChangePriceCommand;
import ro.linic.cloud.command.ChangeStockCommand;
import ro.linic.cloud.command.CreateProductCommand;
import ro.linic.cloud.command.DeleteProductCommand;
import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.pojo.Product;

public interface WoocommerceService {
	ResponseEntity<Product> updatePrice(ChangePriceCommand command);
	ResponseEntity<Product> updateStock(ChangeStockCommand command);
	ResponseEntity<Product> createProduct(CreateProductCommand command);
	ResponseEntity<Product> deleteProduct(DeleteProductCommand command);
	ResponseEntity<Product> updateName(ChangeNameCommand command);
	
	List<Product> allProducts(SyncConnection syncConnection);
	ResponseEntity<Product> deactivateProduct(SyncConnection syncConnection, int wooId);
	ResponseEntity<Product> putProduct(SyncConnection syncConnection, Product wooProduct);
}
