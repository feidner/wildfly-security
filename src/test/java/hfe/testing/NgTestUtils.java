package hfe.testing;

import org.testng.ITestResult;

public class NgTestUtils {

    public static boolean isSingleMethodTest(ITestResult testResult) {
        boolean single = false;
        if(testResult.getTestContext().getAllTestMethods().length == 1) {
            single = true;
        }
        return single;
    }

    public static boolean isSingleClassTest(ITestResult testResult) {
        boolean single = false;
        if(testResult.getTestContext().getCurrentXmlTest().getClasses().size() == 1) {
            single = true;
        }
        return single;
    }

}
