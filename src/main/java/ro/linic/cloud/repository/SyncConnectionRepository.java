package ro.linic.cloud.repository;

import java.util.Optional;

import org.javers.spring.annotation.JaversSpringDataAuditable;
import org.springframework.data.repository.CrudRepository;

import ro.linic.cloud.entity.SyncConnection;

@JaversSpringDataAuditable
public interface SyncConnectionRepository extends CrudRepository<SyncConnection, Integer> {
	Optional<SyncConnection> findByCompanyId(Integer companyId);
}
