package hfe.entity;


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "Principals")
/*,
        uniqueConstraints = {@UniqueConstraint(columnNames = {"FID", "CORRECTION_NUMBER", "PAGE_NUMBER"})},
        indexes = {@Index(name = "ab_name", columnList = "name"),
                @Index(name = "ab_target_state", columnList = "target_state"),
                @Index(name = "ab_autype", columnList = "admin_unit_type")})*/
public class Principals {

    private String principalID;

    @Id
    @Column(name = "PrincipalID", length = 100)
    public String getPrincipalID() {
        return principalID;
    }

    public void setPrincipalID(String principalID) {
        this.principalID = principalID;
    }
}
