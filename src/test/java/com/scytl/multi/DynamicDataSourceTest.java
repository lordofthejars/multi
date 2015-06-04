package com.scytl.multi;

import org.apache.openejb.config.sys.JaxbOpenejb;
import org.apache.openejb.config.sys.Resource;
import org.apache.openejb.config.sys.Resources;
import org.junit.Test;

import javax.ejb.embeddable.EJBContainer;
import javax.inject.Inject;
import javax.naming.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class DynamicDataSourceTest {

    @Inject
    RegisterNewTenant registerNewTenant;

    @Test
    public void route() throws Exception {


        Context ctx = EJBContainer.createEJBContainer().getContext();
        ctx.bind("inject", this);

        List<String> databasesList = new ArrayList<>();

        Resources resources = JaxbOpenejb.unmarshal(Resources.class, DynamicDataSourceTest.class.getResourceAsStream("/my-tenants.xml"));

        List<Resource> tenantResources = resources.getResource();
        for(Resource tenantResource : tenantResources) {
            String id = tenantResource.getId();
            registerNewTenant.registerTenant(id, tenantResource);
            databasesList.add(id);
        }

        String[] databases = databasesList.toArray(new String[databasesList.size()]);
        RoutedPersister ejb = (RoutedPersister) ctx.lookup("java:global/multi/RoutedPersister");
        for (int i = 0; i < 18; i++) {
            // persisting a person on database db -> kind of manual round robin
            String name = "record " + i;
            String db = databases[i % 3];
            ejb.persist(name, db);
        }

        // assert database records number using jdbc
        for (int i = 1; i <= databases.length; i++) {
            Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:db" + i, "sa", "");
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery("select count(*) from PERSON");
            rs.next();
            assertEquals(6, rs.getInt(1));
            st.close();
            connection.close();
        }

        Resource resource = JaxbOpenejb.unmarshal(Resource.class, DynamicDataSourceTest.class.getResourceAsStream("/newtenant.xml"));
        registerNewTenant.registerTenant("database4", resource);

        ejb.persist("record 18", "database4");
        Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:db4", "sa", "");
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("select count(*) from PERSON");
        rs.next();
        assertEquals(1, rs.getInt(1));
        st.close();
        connection.close();

        ctx.close();
    }
    public static String inputStreamToString(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
