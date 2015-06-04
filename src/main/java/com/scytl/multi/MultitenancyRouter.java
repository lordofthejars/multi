package com.scytl.multi;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.assembler.classic.Assembler;
import org.apache.openejb.assembler.classic.OpenEjbConfigurationFactory;
import org.apache.openejb.assembler.classic.ResourceInfo;
import org.apache.openejb.config.ConfigurationFactory;
import org.apache.openejb.config.sys.JaxbOpenejb;
import org.apache.openejb.config.sys.Resource;
import org.apache.openejb.config.sys.Resources;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.resource.jdbc.router.AbstractRouter;
import org.xml.sax.SAXException;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MultitenancyRouter extends AbstractRouter {
    private String tenants;
    private Map<String, DataSource> dataSources = new ConcurrentHashMap<String, DataSource>();
    private ThreadLocal<DataSource> currentDataSource = new ThreadLocal<DataSource>();

    @Inject
    Event<CreatedDataSourceEvent> createdDataSourceEventEvent;
    @Inject
    NewTenantListener newTenantListener;

    @PostConstruct
    public void initializeListener() {
        newTenantListener.initialize(this);
    }

    /**
     * @return the user selected data source if it is set
     *         or the default one
     *  @throws IllegalArgumentException if the data source is not found
     */
    @Override
    public DataSource getDataSource() {
        // lazy init of routed datasources
        if (dataSources == null) {
            throw new IllegalArgumentException("you have to specify at least one datasource");
        }
        // if no datasource is selected use the default one
        if (currentDataSource.get() == null) {
                throw new IllegalArgumentException("you have to specify at least one datasource");
        }

        // the developper set the datasource to use
        return currentDataSource.get();
    }

    public void setTenant(String tenant) {
        if (dataSources == null) {
            throw new IllegalArgumentException("you have to specify at least one datasource");
        }
        if (!dataSources.containsKey(tenant)) {
            throw new IllegalArgumentException("data source called " + tenant + " can't be found.");
        }
        DataSource ds = dataSources.get(tenant);
        currentDataSource.set(ds);
    }

    protected void registerNewTenant(NewTenantEvent newTenantEvent){
        ConfigurationFactory component = (ConfigurationFactory) SystemInstance.get().getComponent(OpenEjbConfigurationFactory.class);
        assembleResource(component, newTenantEvent.getResource());
        DataSource dataSource = registerTenantDataSource(newTenantEvent.getTenantId());
        createdDataSourceEventEvent.fire(new CreatedDataSourceEvent(dataSource));
    }

    private void assembleResource(ConfigurationFactory component, Resource resource) {
        try {
            ResourceInfo resourceInfo = component.configureService(resource, ResourceInfo.class);
            SystemInstance.get().getComponent(Assembler.class).createResource(resourceInfo);
        } catch (OpenEJBException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private DataSource registerTenantDataSource(String ds) {
        try {
            Object o = getOpenEJBResource(ds);
            if (o instanceof DataSource) {
                DataSource datasource = (DataSource)o;
                dataSources.put(ds, datasource);
                return datasource;
            }
        } catch (NamingException e) {
            // ignored
        }
        return null;
    }

    @ApplicationScoped
    public static class NewTenantListener {

        private AtomicReference<MultitenancyRouter> enclosing = new AtomicReference<>();

        public void registerNewTenant(@Observes NewTenantEvent newTenantEvent) {
            enclosing.get().registerNewTenant(newTenantEvent);
        }

        public void initialize(MultitenancyRouter multitenancyRouter) {
            enclosing.set(multitenancyRouter);
        }
    }
}
