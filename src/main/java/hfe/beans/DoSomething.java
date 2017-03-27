package hfe.beans;

import org.jboss.ejb3.annotation.SecurityDomain;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;

@SecurityDomain("henrik")
@RolesAllowed({"ROLEME"})
@Stateless
public class DoSomething {

    public String show() {
        return "Hello";
    }
}
