package hfe;

import org.jboss.ejb3.annotation.SecurityDomain;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;

@SecurityDomain("other")
@RolesAllowed({"ROLEME"})
@Stateless
public class DoSomething {

    public String show() {
        return "Hello";
    }
}
