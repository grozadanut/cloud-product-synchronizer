package ro.linic.cloud.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class DeleteProductCommand {
	Integer companyId;
	Integer productId;
}
