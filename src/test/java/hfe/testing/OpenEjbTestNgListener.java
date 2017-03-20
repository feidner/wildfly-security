package hfe.testing;

import hfe.testing.openejb.EmbeddedContainer;
import hfe.testing.openejb.TransactionBean;
import org.testng.*;

import javax.inject.Inject;

public class OpenEjbTestNgListener implements IInvokedMethodListener, IHookable {
    @Inject
    private TransactionBean transactionBean;

    public OpenEjbTestNgListener() {

    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        EmbeddedContainer.start(this, testResult.getTestClass().getRealClass());
        EmbeddedContainer.applyCdiToObject(testResult.getInstance());
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
    }

    @Override
    public void run(IHookCallBack callBack, ITestResult testResult) {
        transactionBean.runInNewTransaction(() -> callBack.runTestMethod(testResult));
    }
}
