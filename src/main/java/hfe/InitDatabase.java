package hfe;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.persistence.EntityManagerFactory;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.logging.Logger;

@Startup
@Singleton
@TransactionManagement(value= TransactionManagementType.BEAN)
public class InitDatabase {

    //@PersistenceContext(unitName = "hfe")
    private EntityManagerFactory factory;

    @PostConstruct
    public void createDatabase() {
        Logger.getLogger("InitDatabase").info("Create DB");

        try {
            URL clazzes = Collections.list(InitDatabase.class.getClassLoader().getResources("/")).stream().filter(url -> url.getPath().contains("classes")).findAny().get();
            HibernateDDLGenerator.print(clazzes.getPath(), "classes");
        } catch (IOException e) {
            Logger.getLogger("InitDatabase").info(e.getMessage());
        }

    }
}
