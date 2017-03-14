package hfe;

import org.jboss.security.plugins.JBossPolicyRegistration;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.net.URL;
import java.util.Collections;
import java.util.logging.Logger;

@Startup
@Singleton
@TransactionManagement(value= TransactionManagementType.BEAN)
public class InitDatabase {

    @PersistenceContext(unitName = "hfe")
    private EntityManager factory;

    @PostConstruct
    public void createDatabase() {
        Logger.getLogger("InitDatabase").info("Create DB");



        try {

            new InitialContext().bind("java:/policyRegistration", new JBossPolicyRegistration());

            URL clazzes = Collections.list(InitDatabase.class.getClassLoader().getResources("/")).stream().filter(url -> url.getPath().contains("classes")).findAny().get();
            DataSource dataSource = (DataSource)new InitialContext().lookup("jboss/datasources/ExampleDS");
            HibernateDDLGenerator.dropAndCreateEntityTables(dataSource, clazzes.getPath(), "classes");
        } catch (Exception e) {
            Logger.getLogger("InitDatabase").info(e.getMessage());
        }

    }
}
