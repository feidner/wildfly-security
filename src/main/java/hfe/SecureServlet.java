package hfe;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.HttpMethodConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

@WebServlet("/secure/*")
@ServletSecurity(httpMethodConstraints={
        @HttpMethodConstraint(value="GET", rolesAllowed={"ROLEME"}),
        @HttpMethodConstraint(value="POST", rolesAllowed={"ROLEME"})})
    //@HttpMethodConstraint("GET"),
    //@HttpMethodConstraint(value="POST", rolesAllowed={"ROLEM"}),
    //@HttpMethodConstraint(value="TRACE", emptyRoleSemantic= ServletSecurity.EmptyRoleSemantic.DENY) })
public class SecureServlet extends HttpServlet {

    @Inject
    private DoSomething doSomething;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Logger.getLogger("SecureServlet").info(doSomething.show());
        String relativePath = "/WEB-INF" + request.getRequestURI().substring(request.getContextPath().length());
        request.getRequestDispatcher(relativePath).forward(request, response);
    }
}
