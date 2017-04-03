package hfe.testing.openejb;

import java.lang.reflect.Modifier;

@FunctionalInterface
interface ContainerPredicate {
    boolean isContainerTest(Class<?> clazz);

    default boolean isContainerTest(String classString) {
        try {
            Class<?> clazz = Class.forName(classString);
            return !Modifier.isAbstract(clazz.getModifiers()) && isContainerTest(clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
