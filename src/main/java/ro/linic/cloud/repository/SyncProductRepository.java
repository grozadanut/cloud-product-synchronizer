package ro.linic.cloud.repository;

import java.util.List;
import java.util.Optional;

import org.javers.spring.annotation.JaversSpringDataAuditable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import ro.linic.cloud.entity.SyncLine;

@JaversSpringDataAuditable
@RepositoryRestResource
public interface SyncProductRepository extends CrudRepository<SyncLine, Integer> {
	List<SyncLine> findBySyncConnectionIdAndProductIdAndWooId(int syncConnectionId, Integer productId, Integer wooId);
	List<SyncLine> findBySyncConnectionId(int syncConnectionId);
	Optional<SyncLine> findBySyncConnectionIdAndProductId(int syncConnectionId, Integer productId);
}
