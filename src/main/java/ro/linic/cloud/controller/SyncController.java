package ro.linic.cloud.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.service.SyncService;

@RestController
@RequestMapping("/sync")
public class SyncController {

	@Autowired private SyncService syncService;
	
	@Operation(summary = "Main operation for creating a SyncConnection and bulk update")
	@PostMapping
    public ResponseEntity<Object> createSyncConnection(@RequestBody final SyncConnection syncConnection) {
		if (syncConnection.getCompanyId() == null)
			return new ResponseEntity<>(Map.of("companyId", "required"), HttpStatus.BAD_REQUEST);
		if (syncConnection.getInventoryServiceUrl() == null)
			return new ResponseEntity<>(Map.of("inventoryServiceUrl", "required"), HttpStatus.BAD_REQUEST);
		if (syncConnection.getWebsiteUrl() == null)
			return new ResponseEntity<>(Map.of("websiteUrl", "required"), HttpStatus.BAD_REQUEST);
		
        return new ResponseEntity<>(syncService.createConnection(syncConnection), HttpStatus.OK);
    }
	
	@PutMapping("/{id}")
    public ResponseEntity<Object> updateConnection(@PathVariable(name = "id") final Integer syncConnectionId,
    		@RequestParam final String inventoryServiceUrl, @RequestParam final String websiteUrl,
    		@RequestParam(required = false) final String key, @RequestParam(required = false) final String secret) {
        return new ResponseEntity<>(syncService.updateConnection(syncConnectionId, inventoryServiceUrl, websiteUrl, key, secret),
        		HttpStatus.OK);
    }
	
	@DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteSyncConnection(@PathVariable(name = "id") final Integer syncConnectionId) {
		syncService.deleteConnection(syncConnectionId);
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }
	
	@GetMapping
    public ResponseEntity<Object> getSyncConnection() {
        return new ResponseEntity<>(syncService.findAll(), HttpStatus.OK);
    }
}