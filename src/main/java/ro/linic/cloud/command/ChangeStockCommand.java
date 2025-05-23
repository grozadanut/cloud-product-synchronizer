package ro.linic.cloud.command;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ChangeStockCommand {
	Integer companyId;
	Integer productId;
	BigDecimal stock;
}
