package ro.linic.cloud.command;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateProductCommand {
	Integer companyId;
	Integer productId;
	String barcode;
	String name;
	String uom;
	BigDecimal pricePerUom;
}
