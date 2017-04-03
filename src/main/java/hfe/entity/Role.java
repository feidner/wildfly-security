package hfe.entity;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

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

    @Column
    private String roleGroup;

    public Role() {

    }

    public Role(Principal id, String role, String roleGroup) {
        this.principalId = id;
        this.roleId = role;
        this.roleGroup = roleGroup;
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

    public void setRoleGroup(String roleGroup) {
        this.roleGroup = roleGroup;
    }

    public String getRoleGroup() {
        return roleGroup;
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
