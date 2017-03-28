package hfe.beans;

import hfe.entity.Principal;
import hfe.entity.Role;
import hfe.testing.OpenEjbTestNgListener;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Listeners(OpenEjbTestNgListener.class)
public class FillDatabaseTest {

    @PersistenceContext(unitName = "hfe")
    private EntityManager entityManager;

    @DataProvider(name = "users")
    public Object[][] createData1() {
        return new Object[][] {
                { "geral", "password", Stream.of(new Role(new Principal("geral"), "feidner", "Roles")).collect(Collectors.toSet()) },
                { "henrik", "password", Stream.of(new Role(new Principal("henrik"), "grimm", "Roles")).collect(Collectors.toSet()) },
                { "mats", "password", Stream.of(new Role(new Principal("mats"), "feidner", "Roles"), new Role(new Principal("mats"), "grimm", "Roles")).collect(Collectors.toSet()) },
                { "filippa", "password", Stream.of(new Role(new Principal("filippa"), "feidner", "Roles"), new Role(new Principal("filippa"), "grimm", "Roles")).collect(Collectors.toSet()) }
        };
    }

    @Test(dataProvider = "users")
    public void insert(String name, String password, Set<Role> roles) {
        entityManager.persist(new Principal(name, password, roles));
    }
}