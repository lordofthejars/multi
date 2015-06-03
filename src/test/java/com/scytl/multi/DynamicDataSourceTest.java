package com.scytl.multi;

import org.apache.openejb.config.sys.JaxbOpenejb;
import org.apache.openejb.config.sys.Resources;
import org.hsqldb.lib.StringInputStream;
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
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * If you are using openejb.xml the test properties are:
 * <p/>
 * <p/>
 * <!-- Router and datasource -->
 * <Resource id="My Router" type="org.apache.openejb.router.test.DynamicDataSourceTest$DeterminedRouter" provider="org.routertest:DeterminedRouter">
 * DatasourceNames = database1 database2 database3
 * DefaultDataSourceName = database1
 * </Resource>
 * <Resource id="Routed Datasource" type="org.apache.openejb.resource.jdbc.Router" provider="org.router:RoutedDataSource">
 * Router = My Router
 * </Resource>
 * <p/>
 * <!-- real datasources -->
 * <Resource id="database1" type="DataSource">
 * JdbcDriver = org.hsqldb.jdbcDriver
 * JdbcUrl = jdbc:hsqldb:mem:db1
 * UserName = sa
 * Password
 * JtaManaged = true
 * </Resource>
 * <Resource id="database2" type="DataSource">
 * JdbcDriver = org.hsqldb.jdbcDriver
 * JdbcUrl = jdbc:hsqldb:mem:db2
 * UserName = sa
 * Password
 * JtaManaged = true
 * </Resource>
 * <Resource id="database3" type="DataSource">
 * JdbcDriver = org.hsqldb.jdbcDriver
 * JdbcUrl = jdbc:hsqldb:mem:db3
 * UserName = sa
 * Password
 * JtaManaged = true
 * </Resource>
 */
public class DynamicDataSourceTest {

    @Inject
    RegisterNewTenant registerNewTenant;

    @Test
    public void route() throws Exception {
        String[] databases = new String[]{"database1", "database2", "database3"};


        Context ctx = EJBContainer.createEJBContainer().getContext();
        ctx.bind("inject", this);
        RoutedPersister ejb = (RoutedPersister) ctx.lookup("java:global/multi/RoutedPersister");
        for (int i = 0; i < 18; i++) {
            // persisting a person on database db -> kind of manual round robin
            String name = "record " + i;
            String db = databases[i % 3];
            ejb.persist(i, name, db);
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

        Resources resources = JaxbOpenejb.unmarshal(Resources.class, DynamicDataSourceTest.class.getResourceAsStream("/newtenant.xml"));
        registerNewTenant.registerTenant("database4", resources);
        ctx.close();
    }
    public static String inputStreamToString(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
