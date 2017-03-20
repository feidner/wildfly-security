package hfe.beans;

import hfe.entity.Principal;
import hfe.entity.Role;
import hfe.testing.OpenEjbTestNgListener;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Listeners(OpenEjbTestNgListener.class)
public class InitialApplicationTest {

    @Inject
    private InitialApplication initialApplication;

    @PersistenceContext(unitName = "hfe")
    private EntityManager entityManager;

    @DataProvider(name = "users")
    public Object[][] createData1() {
        return new Object[][] {
                { "geral", "password", Stream.of(new Role(new Principal("geral"), "feidner")).collect(Collectors.toSet()) },
                { "henrik", "password", Stream.of(new Role(new Principal("henrik"), "grimm")).collect(Collectors.toSet()) },
                { "mats", "password", Stream.of(new Role(new Principal("mats"), "feidner"), new Role(new Principal("mats"), "grimm")).collect(Collectors.toSet()) },
                { "filippa", "password", Stream.of(new Role(new Principal("filippa"), "feidner"), new Role(new Principal("filippa"), "grimm")).collect(Collectors.toSet()) }
        };
    }



    @Test(dataProvider = "users")
    public void insert(String name, String password, Set<Role> roles) {
        entityManager.persist(new Principal(name, password, roles));
    }
}