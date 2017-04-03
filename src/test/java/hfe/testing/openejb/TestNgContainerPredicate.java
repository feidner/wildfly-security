package hfe.testing.openejb;

import hfe.testing.OpenEjbNgListener;
import hfe.testing.OpenEjbTransactionNgListener;
import hfe.tools.HfeUtils;
import org.testng.ITestNGListener;
import org.testng.annotations.Listeners;

public class TestNgContainerPredicate implements ContainerPredicate {
    @Override
    public boolean isContainerTest(Class<?> clazz) {
        Class<?> topLevelClass = HfeUtils.getTopLevelEnclosingClass(clazz);
        if(topLevelClass.isAnnotationPresent(Listeners.class))  {
            Listeners anno = topLevelClass.getAnnotation(Listeners.class);
            Class<? extends ITestNGListener>[] val = anno.value();
            if (val.length == 1 && (val[0] == OpenEjbTransactionNgListener.class || val[0] == OpenEjbNgListener.class)) {
                return true;
            }
        }
        return false;
    }
}
