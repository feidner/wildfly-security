package hfe.beans;

import hfe.entity.Principal;
import hfe.testing.OpenEjbNgListener;
import hfe.tools.HfeUtils;
import org.hibernate.exception.SQLGrammarException;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.testng.AssertJUnit.assertNull;

@Listeners(OpenEjbNgListener.class)
public class InitialApplicationTest {

    @PersistenceContext(unitName = "hfe")
    private EntityManager entityManager;

    @Inject
    private InitialApplication initialApplication;

    @Inject
    private TransactionBean transactionBean;

    @Test
    public void dropTables() throws Exception {
        initialApplication.dropTables(HfeUtils.getClassesFolderURL());
    }

    @Test
    public void executeFindWithNotAvailableId() throws Exception {
        initialApplication.initial();
        assertNull("Es gibt keine DUD-Id", entityManager.find(Principal.class, "DUD"));
    }

    @Test(expectedExceptions = {SQLGrammarException.class})
    public void executeFindAfterDropDatabase() throws Throwable {
        dropTables();
        try {
            transactionBean.tryToProduceInNewTransaction(() -> entityManager.find(Principal.class, "DUD"));
        } catch (Exception ex) {
            throw ex.getCause().getCause();
        }
    }
}