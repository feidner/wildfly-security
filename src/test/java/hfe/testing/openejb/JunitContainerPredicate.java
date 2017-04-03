package hfe.testing.openejb;

public class JunitContainerPredicate implements ContainerPredicate {
    @Override
    public boolean isContainerTest(Class<?> clazz) {
        /*if (cl.isAnnotationPresent(RunWith.class)) {
            RunWith anno = cl.getAnnotation(RunWith.class);
            Class<?> val = anno.value();
            if (val == TransactionBasedTestRunner.class || val == TransactionDbSetupTestRunner.class) {
                return true;
            }
        }*/
        return false;
    }
}
