package hfe.testing;

import hfe.testing.openejb.EmbeddedContainer;
import hfe.testing.openejb.TransactionBean;
import org.testng.*;

import javax.inject.Inject;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpenEjbTransactionNgListener implements IInvokedMethodListener, IHookable {
    @Inject
    private TransactionBean transactionBean;

    public OpenEjbTransactionNgListener() {

    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        EmbeddedContainer.start(this, testResult.getTestClass().getRealClass(), NgTestUtils.isSingleMethodTest(testResult),
                Stream.of(OpenEjbTransactionNgListener.class.getTypeName(), TransactionBean.class.getTypeName()).collect(Collectors.toSet()),
                Collections.EMPTY_SET);
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
