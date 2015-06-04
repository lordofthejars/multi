package com.scytl.multi;
import org.apache.openejb.config.sys.Resource;
public class NewTenantEvent {

    private String tenantId;
    private Resource resource;

    public NewTenantEvent(String tenantId, Resource resource) {
        this.tenantId = tenantId;
        this.resource = resource;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Resource getResource() {
        return resource;
    }
}
