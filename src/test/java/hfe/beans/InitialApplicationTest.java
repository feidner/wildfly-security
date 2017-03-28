package hfe.beans;

import hfe.entity.Principal;
import hfe.testing.OpenEjbTestNgListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.testng.AssertJUnit.assertNull;

@Listeners(OpenEjbTestNgListener.class)
public class InitialApplicationTest {

    @PersistenceContext(unitName = "hfe")
    private EntityManager entityManager;


    @Test
    public void createDB() {
        assertNull("Es gibt keine DUD-Id", entityManager.find(Principal.class, "DUD"));
    }
}