package hfe.rest;

import javax.ws.rs.*;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

@ApplicationPath("/restme")
public class HfeApplication extends Application {


    @Path("/")
    public static class Service {
        @GET
        @Path("/hello")
        public String hello() {
            Logger.getLogger("Service").info("hello");
            return "hello";
        }

        @POST
        @Path("/logmein")
        public Response login(@FormParam("username") String username, @FormParam("password") String password) {
            Logger.getLogger("Service").info("Login Success for: " + username);
            return seeOther("../");
        }

        @GET
        @Path("/logout")
        public Response logout() {
            Logger.getLogger("Service").info("Logout");
            return seeOther("../");
        }

        private static Response seeOther(String url) {
            try {
                return Response.seeOther(new URI(url)).build();
            } catch (URISyntaxException e) {
                return null;
            }
        }
    }
}

