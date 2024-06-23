package ro.linic.cloud.service;

import java.util.List;

import org.springframework.http.ResponseEntity;

import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.pojo.Product;

public interface WoocommerceApi {

	List<Product> allProducts(SyncConnection syncConnection);
	ResponseEntity<Product> createProduct(SyncConnection syncConnection, Product wooProduct);
	ResponseEntity<Product> putProduct(SyncConnection syncConnection, Product wooProduct);
	ResponseEntity<Product> patchProduct(SyncConnection syncConnection, Product wooProduct);
	ResponseEntity<Product> deactivateProduct(SyncConnection syncConnection, int wooId);
}
