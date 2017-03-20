package hfe.tools;

import org.apache.commons.lang3.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Hilfsklasse für das Erstellen von stack traces.
 */

public class StackTrace {

    private StackTrace() {
    }


    public static String getMethodName(Throwable reason) {
        return getMethodName(reason, 0);
    }

    public static String getMethodName(Throwable reason, int index) {
        return reason.getStackTrace()[index].getMethodName();
    }

    public static String of(Throwable reason) {
        return of(reason, null);
    }

    public static String of(Throwable reason, int lineCount) {
        return lines(of(reason, null), lineCount, 0);
    }

    public static String of(Throwable reason, String enclosing) {
        if (reason == null) {
            return null;
        }
        StringWriter sw = new StringWriter(500);
        PrintWriter pw = new PrintWriter(sw);
        boolean useEnclosing = !StringUtils.isBlank(enclosing);
        if (useEnclosing) {
            pw.println(enclosing + ">>begin");
        }
        reason.printStackTrace(pw);
        if (useEnclosing) {
            pw.println(enclosing + ">>end");
        }
        pw.flush();
        return sw.toString();
    }

    public static String current(String hint) {
        try {
            throw new RuntimeException(hint);
        } catch (RuntimeException ex) {
            return of(ex, hint);
        }
    }

    public static String current() {
        String hint = "StackTrace.current";
        return current(hint);
    }

    public static boolean currentMatches(String regex) {
        return currentMatches(regex, current());
    }

    public static boolean currentMatches(String regex, String current) {
        String[] liness = current.split("\n");
        StringBuilder buf = new StringBuilder();
        for (String line : liness) {
            if (line.trim().matches(regex)) {
                buf.append(line);
                buf.append("\n");
            }
        }
        return !buf.toString().isEmpty();
    }

    public static String current(int lineCount) {
        return current(lineCount, current());
    }

    public static String current(int lineCount, String current) {
        return lines(current, lineCount, 0);
    }

    public static String current(int lineCount, int start) {
        return lines(current(), lineCount, start);
    }

    public static String lines(String lines, int lineCount, int start) {
        String[] liness = lines.split("\n");
        StringBuilder buf = new StringBuilder();
        int l = 0;
        buf.append(liness[0]);
        while (l < lineCount && (start + l) < liness.length) {
            buf.append(liness[start + l++]);
            buf.append("\n");
        }
        buf.append(liness[liness.length - 1]);
        return buf.toString();
    }

}