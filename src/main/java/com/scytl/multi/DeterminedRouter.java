package com.scytl.multi;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.assembler.classic.Assembler;
import org.apache.openejb.assembler.classic.OpenEjbConfigurationFactory;
import org.apache.openejb.assembler.classic.ResourceInfo;
import org.apache.openejb.config.ConfigurationFactory;
import org.apache.openejb.config.Module;
import org.apache.openejb.config.ReadDescriptors;
import org.apache.openejb.config.sys.JaxbOpenejb;
import org.apache.openejb.config.sys.Resource;
import org.apache.openejb.config.sys.Resources;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.resource.jdbc.router.AbstractRouter;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeterminedRouter extends AbstractRouter {
    private String tenants;
    private Map<String, DataSource> dataSources = null;
    private ThreadLocal<DataSource> currentDataSource = new ThreadLocal<DataSource>();

    @Inject
    Person person;

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
            try {
                Object o = getOpenEJBResource(ds);
                if (o instanceof DataSource) {
                    dataSources.put(ds, DataSource.class.cast(o));
                }
            } catch (NamingException e) {
                // ignored
            }
        }
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
        InputStream resourceAsStream = DeterminedRouter.class.getResourceAsStream("/my-tenant.xml");
        loadResources(resourceAsStream);
    }

    private void loadResources(InputStream resourceAsStream) {
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

    /**
     * reset the data source
     */
    public void clear() {
        currentDataSource.remove();
    }

}
