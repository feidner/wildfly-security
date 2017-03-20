package hfe.servlets;

import hfe.beans.DoSomething;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

@WebServlet("/open/*")
public class OpenServlet extends HttpServlet {

    @Inject
    private DoSomething doSomething;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //Logger.getLogger(OpenServlet.class.getSimpleName()).info(doSomething.show());
        String relativePath = "/WEB-INF" + request.getRequestURI().substring(request.getContextPath().length());
        Logger.getLogger(OpenServlet.class.getSimpleName()).info(String.format("forward to %s", relativePath));
        request.getRequestDispatcher(relativePath).forward(request, response);
    }
}
