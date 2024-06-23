package ro.linic.cloud.controller;

import java.util.List;

import org.javers.core.Javers;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.repository.jql.JqlQuery;
import org.javers.repository.jql.QueryBuilder;
import org.javers.shadow.Shadow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.entity.SyncLine;

@RestController
@RequestMapping(value = "/audit")
public class AuditController {

	@Autowired private Javers javers;

    @GetMapping("/syncConnection")
    public String syncConnectionChanges() {
        final QueryBuilder jqlQuery = QueryBuilder.byClass(SyncConnection.class);
        return javers.findChanges(jqlQuery.build()).prettyPrint();
    }

    @GetMapping("/syncConnection/shadows")
    public String syncConnectionShadows() {
        final JqlQuery jqlQuery = QueryBuilder.byClass(SyncConnection.class).withChildValueObjects().build();
        final List<Shadow<SyncConnection>> shadows = javers.findShadows(jqlQuery);
        return javers.getJsonConverter().toJson(shadows);
    }
    
    @GetMapping("/syncLine")
    public String syncProductChanges() {
        final QueryBuilder jqlQuery = QueryBuilder.byClass(SyncLine.class);
        return javers.findChanges(jqlQuery.build()).prettyPrint();
    }
    
    @GetMapping("/all")
    public String allChanges() {
        final QueryBuilder jqlQuery = QueryBuilder.anyDomainObject();
        return javers.findChanges(jqlQuery.build()).prettyPrint();
    }
    
    @GetMapping("/all/snapshots")
    public String allSnapshots() {
        final QueryBuilder jqlQuery = QueryBuilder.anyDomainObject();
        final List<CdoSnapshot> snapshots = javers.findSnapshots(jqlQuery.build());
        return javers.getJsonConverter().toJson(snapshots);
    }
}