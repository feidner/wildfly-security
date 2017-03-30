package hfe.beans;

import hfe.testing.OpenEjbNgListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.annotation.security.RunAs;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.concurrent.Callable;

@Listeners(OpenEjbNgListener.class)
public class DoSomethingTest {

    @Inject
    private DoSomething doSomething;

    @Inject
    private RoleMeExecuter roleMeExecuter;

    @Test
    public void hastShowRights() throws Exception {
        roleMeExecuter.call(() -> doSomething.show());
    }

    @Stateless
    @RunAs("ROLEME")
    public static class RoleMeExecuter {
        public <V> V call(Callable<V> callable) throws Exception {
            return callable.call();
        }
    }
}

