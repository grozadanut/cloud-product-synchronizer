package ro.linic.cloud.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ro.linic.cloud.command.ChangeNameCommand;
import ro.linic.cloud.command.ChangePriceCommand;
import ro.linic.cloud.command.ChangeStockCommand;
import ro.linic.cloud.command.CreateProductCommand;
import ro.linic.cloud.command.DeleteProductCommand;
import ro.linic.cloud.pojo.Product;
import ro.linic.cloud.service.SyncService;

@RestController
@RequestMapping("/update")
public class UpdateController {

	@Autowired private SyncService syncService;

	@PostMapping("/createProduct")
    public ResponseEntity<Product> createProduct(@RequestBody final CreateProductCommand command) {
        return syncService.createProduct(command);
    }

	@PostMapping("/deleteProduct")
    public ResponseEntity<Product> deleteProduct(@RequestBody final DeleteProductCommand command) {
        return syncService.deleteProduct(command);
    }
	
	@PostMapping("/price")
    public ResponseEntity<Product> updatePrice(@RequestBody final ChangePriceCommand command) {
        return syncService.updatePrice(command);
    }
	
	@PostMapping("/stock")
    public ResponseEntity<Product> updateStock(@RequestBody final ChangeStockCommand command) {
        return syncService.updateStock(command);
    }
	
	@PostMapping("/name")
    public ResponseEntity<Product> updateName(@RequestBody final ChangeNameCommand command) {
        return syncService.updateName(command);
    }
}
