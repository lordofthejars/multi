package com.scytl.multi;
import org.apache.openejb.config.sys.Resources;
public class NewTenantEvent {

    private String tenantId;
    private Resources resources;

    public NewTenantEvent(String tenantId, Resources resources) {
        this.tenantId = tenantId;
        this.resources = resources;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Resources getResources() {
        return resources;
    }
}
