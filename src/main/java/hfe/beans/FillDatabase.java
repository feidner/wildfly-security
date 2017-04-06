package hfe.beans;

import hfe.entity.Principal;
import hfe.entity.Role;
import hfe.tools.HfeUtils;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Stateless
@Transactional(Transactional.TxType.REQUIRES_NEW)
public class FillDatabase {

    @PersistenceContext(unitName = "hfe")
    private EntityManager entitManager;

    public void insertData() throws NoSuchAlgorithmException {
        getEntityManager().persist(new Principal("feidner", HfeUtils.createPasswordHash("feidner", "10Hendi!"), Stream.of(new Role(new Principal("feidner"), "ROLEME", "Roles")).collect(Collectors.toSet())));
    }

    private EntityManager getEntityManager() {
        return entitManager;
    }
}
