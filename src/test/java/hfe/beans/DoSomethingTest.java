package hfe.beans;

import hfe.testing.OpenEjbNgListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.annotation.security.RunAs;
import javax.ejb.EJBAccessException;
import javax.inject.Inject;
import java.util.concurrent.Callable;

@Listeners(OpenEjbNgListener.class)
public class DoSomethingTest {

    @Inject
    private DoSomething doSomething;

    @Inject
    private RoleMeExecuterTest roleMeExecuterTest;

    @Test
    public void hasShowRights() throws Exception {
        roleMeExecuterTest.call(() -> doSomething.show());
    }

    @Test(expectedExceptions = {EJBAccessException.class})
    public void noRights() {
        doSomething.show();
    }

    @RunAs("ROLEME")
    public static class RoleMeExecuterTest {
        public <V> V call(Callable<V> callable) throws Exception {
            return callable.call();
        }
    }
}

