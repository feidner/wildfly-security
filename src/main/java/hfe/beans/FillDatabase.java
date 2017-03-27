package hfe.beans;

import hfe.entity.Principal;
import hfe.entity.Role;
import org.jboss.security.auth.spi.Util;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Stateless
@Transactional(Transactional.TxType.REQUIRES_NEW)
public class FillDatabase {

    @PersistenceContext(unitName = "hfe")
    private EntityManager entitManager;

    public void insertData() {

        getEntityManager().persist(new Principal("feidner", createPasswordHash("10Hendi!"), Stream.of(new Role(new Principal("feidner"), "ROLEME")).collect(Collectors.toSet())));
    }

    private String createPasswordHash(String password) {
        String passwordHash = Util.createPasswordHash("SHA-256", "base64", null, "feidner", password , null);
        return passwordHash;
    }

    private EntityManager getEntityManager() {
        return entitManager;
    }
}
