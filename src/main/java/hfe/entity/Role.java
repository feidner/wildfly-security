package hfe.entity;

import javax.persistence.*;
import java.io.Serializable;

@Entity
public class Role implements Serializable {

    @Id
    @ManyToOne
    @JoinColumn(name="id")
    private Principal principalId;

    @Id
    private String roleId;

    public Role(Principal id, String role) {
        this.principalId = id;
        this.roleId = role;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public Principal getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(Principal principalId) {
        this.principalId = principalId;
    }
}
