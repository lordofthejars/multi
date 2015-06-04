package com.scytl.multi;

import org.apache.openejb.config.sys.Resource;

import javax.enterprise.event.Event;
import javax.inject.Inject;

public class RegisterNewTenant {


    @Inject
    Event<NewTenantEvent> newTenantEventEvent;

    public void registerTenant(String tenantId, Resource resource) {

            newTenantEventEvent.fire(new NewTenantEvent(tenantId, resource));

    }
}
