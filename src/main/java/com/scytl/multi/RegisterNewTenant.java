package com.scytl.multi;

import org.apache.openejb.config.sys.JaxbOpenejb;
import org.apache.openejb.config.sys.Resources;
import org.hsqldb.lib.StringInputStream;
import org.xml.sax.SAXException;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;

public class RegisterNewTenant {


    @Inject
    Event<NewTenantEvent> newTenantEventEvent;

    public void registerTenant(String tenantId, Resources resources) {

            newTenantEventEvent.fire(new NewTenantEvent(tenantId, resources));

    }
}
