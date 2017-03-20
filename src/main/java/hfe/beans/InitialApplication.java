package hfe.beans;

import hfe.tools.HibernateDDLGenerator;
import org.jboss.security.plugins.JBossPolicyRegistration;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.sql.DataSource;
import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Startup
@Singleton
@TransactionManagement(value= TransactionManagementType.BEAN)
public class InitialApplication {

    private static Map<String, Set<Function<String, String>>> DROPP_ALL_STATEMENTS_TO_DATABASES;

    //StreamSupport.stream(Spliterators.spliteratorUnknownSize(dropDatabases.iterator(), 0), false).forEach(drop -> drop.drop(connection));

    static {
        DROPP_ALL_STATEMENTS_TO_DATABASES = new HashMap<>();
        DROPP_ALL_STATEMENTS_TO_DATABASES.put("H2", Stream.of((Function<String, String>) tablename -> "DROP ALL OBJECTS").collect(Collectors.toSet()));
        DROPP_ALL_STATEMENTS_TO_DATABASES.put("HSQL", Stream.of((Function<String, String>) tablename -> "DROP SCHEMA PUBLIC CASCADE").collect(Collectors.toSet()));
    }

    @PersistenceContext(unitName = "hfe")
    private EntityManager entityManager;

    @PostConstruct
    public void initial() {
        try {
            createDatabase();
            bindPoliceRegistration();
        } catch (Exception e) {
            Logger.getLogger(InitialApplication.class.getSimpleName()).log(Level.SEVERE, "RAUS", e);
            //throw new RuntimeException(e);
        }
    }

    private void createDatabase() throws Exception {

        List<URL> resources = Collections.list(InitialApplication.class.getClassLoader().getResources("."));
        URL clazzes = resources.stream().filter(url -> url.getPath().contains("classes") && !url.getPath().contains("test")).findAny().get();

        DataSource dataSource = (DataSource) entityManager.getEntityManagerFactory().getProperties().values().stream().filter(propObj -> propObj instanceof DataSource).findFirst().get();
        Logger.getLogger(InitialApplication.class.getSimpleName()).info("Drop DB");
        HibernateDDLGenerator.dropEntityTables(dataSource, clazzes);
        dropAllTablesIfStillSomeExists(dataSource.getConnection(), getTableNames());
        Logger.getLogger(InitialApplication.class.getSimpleName()).info("Create DB");
        HibernateDDLGenerator.createEntityTables(dataSource, clazzes);
        Logger.getLogger(getClass().getSimpleName()).info("Tabellen.....");
        getTableNames().forEach(name -> Logger.getLogger(getClass().getSimpleName()).info(name));
    }

    private void bindPoliceRegistration() throws NamingException {
        new InitialContext().bind("java:/policyRegistration", new JBossPolicyRegistration());
    }

    private List<String> getTableNames() {
        Query query = entityManager.createNativeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME");
        return ((List<String>) query.getResultList());
    }

    private void dropAllTablesIfStillSomeExists(Connection connection, List<String> tableNames) {
        if(!tableNames.isEmpty()) {
            try {
                DatabaseMetaData metaData = connection.getMetaData();
                String productName = metaData.getDatabaseProductName();
                Optional<Set<Function<String, String>>> droppers = DROPP_ALL_STATEMENTS_TO_DATABASES.keySet().
                        stream().
                        filter(productName::contains).
                        map(ident -> DROPP_ALL_STATEMENTS_TO_DATABASES.get(ident)).
                        findAny();
                if (droppers.isPresent()) {
                    Set<String> sqls = new HashSet<>();
                    Set<Function<String, String>> dropFunctions = droppers.get();
                    tableNames.forEach(tableName -> dropFunctions.forEach(drop -> sqls.add(drop.apply(tableName))));
                    Statement st = connection.createStatement();
                    for (String sql : sqls) {
                        st.execute(sql);
                    }
                    st.close();
                } else {
                    Logger.getLogger(getClass().getSimpleName()).info(String.format("Es ist eine %s, aber keine %s", productName, getClass().getSimpleName()));
                }
            } catch (SQLException e) {
                Logger.getLogger(getClass().getSimpleName()).log(Level.SEVERE, "RAUS", e);
                throw new RuntimeException(e);
            }
        }
    }

}

