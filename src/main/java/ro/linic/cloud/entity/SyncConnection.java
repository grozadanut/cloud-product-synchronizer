package ro.linic.cloud.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.With;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString @With
public class SyncConnection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Integer id;

	@NotNull private Integer companyId;

	@NotNull private String inventoryServiceUrl;
	@NotNull private String websiteUrl;
	private String websiteKey;
	private String websiteSecret;
}
