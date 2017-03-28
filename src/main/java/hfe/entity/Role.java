package hfe.entity;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(getPrincipalId())
                .append(getRoleId())
                .toHashCode();
    }
}
