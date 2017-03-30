package hfe.testing;

import hfe.testing.openejb.EmbeddedContainer;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpenEjbNgListener implements IInvokedMethodListener {

    public OpenEjbNgListener() {

    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        EmbeddedContainer.start(this, testResult.getTestClass().getRealClass(),
                Stream.of(OpenEjbNgListener.class.getTypeName()).collect(Collectors.toSet()),
                Stream.of(".*InitialApplication.*").collect(Collectors.toSet()));
        EmbeddedContainer.applyCdiToObject(testResult.getInstance());
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
    }

}
