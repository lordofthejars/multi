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
    private Map<String, DataSource> dataSources = null;
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
     * @param tenantsList datasource resource name, separator is a space
     */
    public void setTenants(String tenantsList) {
        tenants = tenantsList;
    }

    /**
     * lookup datasource in openejb resources
     */
    private void init() {
        readMyTenantFile();
        dataSources = new ConcurrentHashMap<String, DataSource>();
        for (String ds : tenants.split(" ")) {
            DataSource dataSource = registerTenantDataSource(ds);
            createdDataSourceEventEvent.fire(new CreatedDataSourceEvent(dataSource));
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

    /**
     * @return the user selected data source if it is set
     *         or the default one
     *  @throws IllegalArgumentException if the data source is not found
     */
    @Override
    public DataSource getDataSource() {
        // lazy init of routed datasources
        if (dataSources == null) {
            init();
        }
        // if no datasource is selected use the default one
        if (currentDataSource.get() == null) {
                throw new IllegalArgumentException("you have to specify at least one datasource");
        }

        // the developper set the datasource to use
        return currentDataSource.get();
    }

    private void readMyTenantFile() {
        InputStream resourceAsStream = MultitenancyRouter.class.getResourceAsStream("/my-tenant.xml");
        loadResources(resourceAsStream);
    }

    private void loadResources(InputStream resourceAsStream) {
        Resources resources = unmarshallResources(resourceAsStream);
        assembleResources(resources);
    }

    private void assembleResources(Resources resources) {
        ConfigurationFactory component = (ConfigurationFactory) SystemInstance.get().getComponent(OpenEjbConfigurationFactory.class);
        List<Resource> resourcesList = resources.getResource();
        for (Resource resource : resourcesList) {
            try {
                ResourceInfo resourceInfo = component.configureService(resource, ResourceInfo.class);
                SystemInstance.get().getComponent(Assembler.class).createResource(resourceInfo);
            } catch (OpenEJBException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    private Resources unmarshallResources(InputStream resourceAsStream) {
        Resources resources = null;
        try {
            resources = JaxbOpenejb.unmarshal(Resources.class, resourceAsStream);
        } catch (ParserConfigurationException e) {
            throw new IllegalArgumentException(e);
        } catch (SAXException e) {
            throw new IllegalArgumentException(e);
        } catch (JAXBException e) {
            throw new IllegalArgumentException(e);
        }
        return resources;
    }

    /**
     *
     * @param tenant data source name
     */
    public void setTenant(String tenant) {
        if (dataSources == null) {
            init();
        }
        if (!dataSources.containsKey(tenant)) {
            throw new IllegalArgumentException("data source called " + tenant + " can't be found.");
        }
        DataSource ds = dataSources.get(tenant);
        currentDataSource.set(ds);
    }

    public void registerNewTenant(NewTenantEvent newTenantEvent){
        assembleResources(newTenantEvent.getResources());
        DataSource dataSource = registerTenantDataSource(newTenantEvent.getTenantId());
        createdDataSourceEventEvent.fire(new CreatedDataSourceEvent(dataSource));
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
