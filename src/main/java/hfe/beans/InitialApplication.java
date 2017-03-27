package hfe.beans;

import hfe.tools.HibernateDDLGenerator;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.jboss.security.plugins.JBossPolicyRegistration;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Startup
@Singleton
//@Transactional(REQUIRES_NEW)
@TransactionManagement(TransactionManagementType.BEAN)
public class InitialApplication{

    private static final Map<String, String> PUBLIC_SCHEMA_NAMES;
    private static final Map<String, Set<Function<String, String>>> DROPP_ALL_STATEMENTS_TO_DATABASES;

    //StreamSupport.stream(Spliterators.spliteratorUnknownSize(dropDatabases.iterator(), 0), false).forEach(drop -> drop.drop(connection));

    static {
        PUBLIC_SCHEMA_NAMES = new HashMap<>();
        PUBLIC_SCHEMA_NAMES.put("H2", "PUBLIC");
        PUBLIC_SCHEMA_NAMES.put("HSQL", "PUBLIC");
        PUBLIC_SCHEMA_NAMES.put("Derby", "APP");
        DROPP_ALL_STATEMENTS_TO_DATABASES = new HashMap<>();
        DROPP_ALL_STATEMENTS_TO_DATABASES.put("H2", Stream.of((Function<String, String>) tablename -> "DROP ALL OBJECTS").collect(Collectors.toSet()));
        DROPP_ALL_STATEMENTS_TO_DATABASES.put("HSQL", Stream.of((Function<String, String>) tablename -> "DROP SCHEMA PUBLIC CASCADE").collect(Collectors.toSet()));
        DROPP_ALL_STATEMENTS_TO_DATABASES.put("Derby", Stream.of((Function<String, String>) tablename -> String.format("drop table %s", tablename)).collect(Collectors.toSet()));
    }

    @PersistenceContext(unitName = "hfe")
    private EntityManager entityManager;

    private EntityManager getEntityManager() {
        return entityManager;
    }

    @Inject
    private FillDatabase fillDatabase;

    @PostConstruct
    public void initial() {
        try {
            createDatabase();
            fillDatabase.insertData();
            bindPoliceRegistration();
        } catch (Exception e) {
            Logger.getLogger(InitialApplication.class.getSimpleName()).log(Level.SEVERE, "RAUS", e);
            //throw new RuntimeException(e);
        }
    }

    private String getDialect() throws Exception {
        Session session = getSession();
        Dialect dbDialect = (Dialect) PropertyUtils.getProperty(session.getSessionFactory(), "dialect");
        return dbDialect.getClass().getCanonicalName();
    }

    private Session getSession() throws Exception {
        Object delegate = getEntityManager().getDelegate();
        Session session = delegate instanceof EntityManager ? (Session)PropertyUtils.getProperty(delegate, "session") : (Session) getEntityManager().getDelegate();
        return session;
    }

    private DataSource getDataSource() throws SQLException {
        DataSource dataSource = (DataSource) getEntityManager().getEntityManagerFactory().getProperties().get(AvailableSettings.DATASOURCE);
        if(dataSource == null) {
            dataSource = (DataSource) getEntityManager().getEntityManagerFactory().getProperties().values().stream()
                    .filter(propObj -> propObj instanceof DataSource)
                    .findAny()
                    .orElseThrow(() -> new RuntimeException("DataSource kann nicht gefunden werden"));
        }
        return dataSource;
    }

    private TransactionManager lookupTransactionManager() {
        try {
            return InitialContext.doLookup("java:jboss/TransactionManager");
        } catch (NamingException e) {
            throw new RuntimeException("Unable to lookup transaction manager", e);
        }
    }

    private void createDatabase() throws Exception {

        List<URL> resources = Collections.list(InitialApplication.class.getClassLoader().getResources("."));
        URL clazzes = resources.stream().filter(url -> url.getPath().contains("classes") && !url.getPath().contains("test")).findAny().orElseThrow(() -> new RuntimeException("Verzeichnis classes kann nicht gefunden werden"));

        DataSource dataSource = getDataSource();
        String dialect = getDialect();
        Logger.getLogger(InitialApplication.class.getSimpleName()).info("DB Dialect: " + dialect);
        Logger.getLogger(InitialApplication.class.getSimpleName()).info("Drop DB");

        HibernateDDLGenerator.dropEntityTables(dataSource, dialect, clazzes);
        dropAllTablesIfStillSomeExists(dataSource.getConnection());
        Logger.getLogger(InitialApplication.class.getSimpleName()).info("Create DB");
        HibernateDDLGenerator.createEntityTables(dataSource, dialect, clazzes);

        Logger.getLogger(getClass().getSimpleName()).info("Tabellen.....");
        getTableNames(dataSource.getConnection()).forEach(name -> Logger.getLogger(getClass().getSimpleName()).info(name));
    }

    private void bindPoliceRegistration() throws NamingException {
        new InitialContext().bind("java:/policyRegistration", new JBossPolicyRegistration());
    }

    private List<String> getTableNames(Connection connection) throws SQLException {
        //Query query = entityManager.createNativeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME");
        //tableNames = ((List<String>) query.getResultList());
        DatabaseMetaData metaData = connection.getMetaData();
        String productName = metaData.getDatabaseProductName();
        String schemaPattern = PUBLIC_SCHEMA_NAMES.keySet()
                .stream()
                .filter(productName::contains)
                .map(PUBLIC_SCHEMA_NAMES::get)
                .findAny()
                .orElseThrow(() -> new RuntimeException("SchemaPattern existiert nicht"));
        ResultSet rs = metaData.getTables(null, schemaPattern, null, null);
        List<String> tableNames = new MapListHandler().handle(rs).stream().map(stringObjectMap -> (String)stringObjectMap.get("TABLE_NAME")).collect(Collectors.toList());
        rs.close();
        return tableNames;
    }

    private void dropAllTablesIfStillSomeExists(Connection connection) throws SQLException {
        List<String> tableNames = getTableNames(connection);
        if(!tableNames.isEmpty()) {
            try {

                DatabaseMetaData metaData = connection.getMetaData();
                String productName = metaData.getDatabaseProductName();
                Optional<Set<Function<String, String>>> droppers = DROPP_ALL_STATEMENTS_TO_DATABASES.keySet()
                        .stream()
                        .filter(productName::contains)
                        .map(DROPP_ALL_STATEMENTS_TO_DATABASES::get)
                        .findAny();
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

