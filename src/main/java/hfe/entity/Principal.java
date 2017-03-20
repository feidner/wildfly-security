package hfe.entity;


import javax.persistence.*;
import java.util.Collection;

@Entity
/*,
        uniqueConstraints = {@UniqueConstraint(columnNames = {"FID", "CORRECTION_NUMBER", "PAGE_NUMBER"})},
        indexes = {@Index(name = "ab_name", columnList = "name"),
                @Index(name = "ab_target_state", columnList = "target_state"),
                @Index(name = "ab_autype", columnList = "admin_unit_type")})*/
public class Principal {

    @Id
    private String id;
    @Column
    private String password;

    @OneToMany(mappedBy = "principalId", cascade = {CascadeType.PERSIST})
    private Collection<Role> roles;

    public Principal(String id, String password, Collection<Role> roles) {
        this.id = id;
        this.password = password;
        this.roles = roles;
    }

    public Principal(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Collection<Role> getRoles() {
        return roles;
    }

    public void setRoles(Collection<Role> roles) {
        this.roles = roles;
    }
}
