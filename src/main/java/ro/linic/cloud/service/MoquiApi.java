package ro.linic.cloud.service;

import org.springframework.http.ResponseEntity;

import ro.linic.cloud.command.ChangeNameCommand;
import ro.linic.cloud.command.ChangePriceCommand;
import ro.linic.cloud.command.CreateProductCommand;
import ro.linic.cloud.command.DeleteProductCommand;

public interface MoquiApi {
	ResponseEntity<String> createProduct(CreateProductCommand command);
	ResponseEntity<String> updateName(ChangeNameCommand command);
	ResponseEntity<String> deleteProduct(DeleteProductCommand command);
	ResponseEntity<String> updatePrice(ChangePriceCommand changePriceCommand);
}
