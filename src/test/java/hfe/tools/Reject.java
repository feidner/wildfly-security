package hfe.tools;


import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * This class provides some services that allow you to check for
 * - preconditions
 * - postconditions
 * - invariants
 */
public class Reject {

    /**
     * Privater Konstruktor, damit:
     * - niemand Exemplare kreiert
     * - niemand eine Unterklasse anlegt
     */
    private Reject() {

    }

    public static void ifTrue(boolean condition) {
        ifTrue("Condition should not be met", condition);
    }

    public static void ifTrue(String message, boolean condition) {
        if (condition) {
            throw developmentError(message);
        }
    }

    public static void ifFalse(boolean condition) {
        ifFalse("Condition should be met", condition);
    }

    public static void ifFalse(String message, boolean condition) {
        if (!condition) {
            throw developmentError(message);
        }
    }

    public static void ifNull(Object o) {
        ifNull("Object should not be null", o);
    }

    public static void ifNull(String message, Object o) {
        if (o == null) {
            throw developmentError(message);
        }
    }

    public static void ifNotNull(String message, Object o) {
        if (o != null) {
            throw developmentError(message);
        }
    }

    public static void ifNotNull(Object o) {
        ifNotNull("Reference is expected to be null", o);
    }

    public static void ifEmpty(String s) {
        ifEmpty("String should not be empty", s);
    }

    public static void ifEmpty(String msg, String s) {
        if (s == null || s.isEmpty() || s.trim().isEmpty()) {
            throw developmentError(msg);
        }
    }

    public static void ifContains(String msg, String s, String element) {
        if (s == null || element == null) {
            return;
        }
        if (s.contains(element)) {
            throw developmentError(msg);
        }
    }

    public static void ifContains(String s, String element) {
        ifContains("String is not allowed to contain " + element, s, element);
    }

    public static void ifNotEmpty(String s) {
        ifNotEmpty("String should be empty", s);
    }

    public static void ifNotOfType(Object instance, Class<?> type, String msg) {
        if (!type.isAssignableFrom(instance.getClass())) {
            throw developmentError(msg);
        }
    }

    public static void ifNotEmpty(String msg, String s) {
        if (s != null && !s.trim().isEmpty()) {
            throw developmentError(msg);
        }
    }

    public static void ifEmpty(Collection<?> c) {
        ifEmpty("Collection should not be empty", c);
    }

    public static void ifEmpty(String msg, Collection<?> c) {
        if (c == null || c.isEmpty()) {
            throw developmentError(msg);
        }
    }

    public static void ifEmpty(Object[] a) {
        ifEmpty("Array should not be empty", a);
    }

    public static void ifEmpty(String msg, Object[] a) {
        if (a == null || a.length == 0) {
            throw developmentError(msg);
        }
    }

    public static void ifEmpty(Map<?, ?> c) {
        ifEmpty("Collection should not be empty", c);
    }

    public static void ifEmpty(String msg, Map<?, ?> c) {
        if (c == null || c.isEmpty()) {
            throw developmentError(msg);
        }
    }

    public static void ifNotSized(Collection<?> c, int size) {
        ifNotSized("Collection size (" + getActualCollectionSize(c) + ") does not match the expected size (" + size + ")", c, size);
    }

    public static void ifNotSized(String msg, Collection<?> c, int size) {
        int actualSize = getActualCollectionSize(c);
        if (size != actualSize) {
            throw developmentError(msg);
        }
    }

    private static int getActualCollectionSize(Collection<?> c) {
        if (c == null) {
            return 0;
        }
        return c.size();
    }

    public static void ifNotSized(Map<?, ?> map, int size) {
        ifNotSized("Map size (" + getActualMapSize(map) + ") does not match the expected size (" + size + ")", map, size);
    }

    public static void ifNotSized(String msg, Map<?, ?> map, int size) {
        int actualSize = getActualMapSize(map);
        if (size != actualSize) {
            throw developmentError(msg);
        }
    }

    private static int getActualMapSize(Map<?, ?> c) {
        if (c == null) {
            return 0;
        }
        return c.size();
    }

    public static void ifNotEmpty(Collection<?> c) {
        ifNotEmpty("Collection should be empty", c);
    }

    public static void ifNotEmpty(String msg, Collection<?> c) {
        if (c != null && !c.isEmpty()) {
            throw developmentError(msg);
        }
    }

    public static void ifEqual(Object one, Object two) {
        ifEqual("Objects should not be equal", one, two);
    }

    public static void ifEqual(String msg, Object one, Object two) {
        if (isEqual(one, two)) {
            throw developmentError(msg);
        }
    }

    public static void ifNotEqual(Object one, Object two) {
        ifNotEqual("Objects should be equal", one, two);
    }

    public static void ifNotEqual(String msg, Object one, Object two) {
        if (!isEqual(one, two)) {
            throw developmentError(msg);
        }
    }

    private static boolean isEqual(Object one, Object two) {
        if (one == null) {
            return two == null;
        }
        return two != null && one.equals(two);
    }

    public static void ifNegative(long value) {
        ifNegative("Value is not allowed to be negative", value);
    }

    public static void ifNegative(String msg, long value) {
        if (value < 0) {
            throw developmentError(msg);
        }
    }

    public static void ifNegative(Number value) {
        ifNegative("Value is not allowed to be negative", value);
    }

    public static void ifNegative(String msg, Number value) {
        if (value != null && value.doubleValue() < 0.0) {
            throw developmentError(msg);
        }
    }

    public static RuntimeException developmentError(String msg) {
        return new RuntimeException(msg);
    }

    /**
     * @param msg  Der Fehlertext formatiert wie {@link String#format(String, Object...)}
     * @param args Die Parameter des Fehlertext
     * @return eine neue {@link RuntimeException}
     * @see String#format(String, Object...)
     */
    public static RuntimeException developmentError(String msg, Object... args) {
        args = substituteWithSupplierValues(args);
        return new RuntimeException(String.format(msg, args));
    }

    private static Object[] substituteWithSupplierValues(Object[] args) {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Supplier) {
                result[i] = ((Supplier) args[i]).get();
            } else {
                result[i] = args[i];
            }
        }
        return result;
    }

    public static RuntimeException developmentError(Throwable cause) {
        return new RuntimeException(cause);
    }

    public static RuntimeException developmentError(String msg, Throwable cause) {
        return new RuntimeException(msg, cause);
    }
}
