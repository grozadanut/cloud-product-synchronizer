package ro.linic.cloud.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
public class CreateNotificationCommand {
	Integer companyId;
	String notification;
}
