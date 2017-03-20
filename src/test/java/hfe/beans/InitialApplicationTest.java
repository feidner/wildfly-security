package hfe.beans;

import hfe.entity.Principal;
import hfe.entity.Role;
import hfe.testing.OpenEjbTestNgListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Listeners(OpenEjbTestNgListener.class)
public class InitialApplicationTest {

    @Inject
    private InitialApplication initialApplication;

    @PersistenceContext(unitName = "hfe")
    private EntityManager entityManager;

    @Test
    public void createDatabase() {
    }

    @Test
    public void insert() {
        entityManager.persist(new Principal("feidner", "huhu",
                Stream.of(new Role(new Principal("feidner"), "mats"), new Role(new Principal("feidner"), "geral")).collect(Collectors.toSet())));
    }
}