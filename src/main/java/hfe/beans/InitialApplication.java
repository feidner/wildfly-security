package hfe.beans;

import hfe.tools.HfeUtils;
import hfe.tools.HibernateDDLGenerator;
import org.apache.commons.beanutils.PropertyUtils;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
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
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Startup
@Singleton
@TransactionManagement(TransactionManagementType.BEAN)
public class InitialApplication {

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
            URL classesURL = HfeUtils.getClassesFolderURL();
            dropTables(classesURL);
            createDatabase(classesURL);
            fillDatabase.insertData();
            bindPoliceRegistration();
        } catch (Exception e) {
            Logger.getLogger(InitialApplication.class.getSimpleName()).log(Level.SEVERE, "RAUS", e);
            //throw new RuntimeException(e);
        }
    }

    private String getDialect() throws Exception {
        Session session = getSession();
        SessionFactoryImplementor factory = (SessionFactoryImplementor)session.getSessionFactory();
        Dialect dbDialect = factory.getServiceRegistry().getService(JdbcServices.class).getJdbcEnvironment().getDialect();
        Logger.getLogger(InitialApplication.class.getSimpleName()).info("DB Dialect: " + dbDialect.getClass().getCanonicalName());
        return dbDialect.getClass().getCanonicalName();
    }

    private Session getSession() throws Exception {
        Object delegate = getEntityManager().getDelegate();
        Session session = delegate instanceof EntityManager ? (Session)PropertyUtils.getProperty(delegate, "session") : (Session) getEntityManager().getDelegate();
        return session;
    }

    DataSource getDataSource() throws SQLException {
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

    public void dropTables(URL classes) throws Exception {
        DataSource dataSource = getDataSource();
        Logger.getLogger(InitialApplication.class.getSimpleName()).info("Drop DB");
        HibernateDDLGenerator.dropEntityTables(dataSource, getDialect(), classes);
        HfeUtils.dropAllTablesIfStillSomeExists(dataSource.getConnection());
    }

    private void createDatabase(URL classes) throws Exception {
        DataSource dataSource = getDataSource();
        String dialect = getDialect();
        Logger.getLogger(InitialApplication.class.getSimpleName()).info("Create DB");
        HibernateDDLGenerator.createEntityTables(dataSource, dialect, classes);
        Logger.getLogger(getClass().getSimpleName()).info("Tabellen.....");
        HfeUtils.getTableNames(dataSource.getConnection()).forEach(name -> Logger.getLogger(getClass().getSimpleName()).info(name));
    }

    private void bindPoliceRegistration() throws NamingException {
        Object obj = new InitialContext().lookup("java:/policyRegistration");
        if(obj == null) {
            new InitialContext().bind("java:/policyRegistration", new JBossPolicyRegistration());
        }
    }
}

