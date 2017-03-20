package hfe.tools;

import org.apache.commons.lang3.time.FastDateFormat;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Exemplare dienen dem Messen einer Dauer (Duration).
 * <p/>
 * Man started die Uhr einfach und stoppt sie wieder um die Dauer abzulesen.
 * Dann kann man wieder starten und stoppen usw. bis man sie nicht mehr braucht.
 * Mann kann auch mehrere Messungen auf den selben Startzeitpunkt machen indem man
 * einfach mehrfach stoppt.
 */
public class StopWatch {

    private static final String FORMAT_PATTERN_WITH_HOURS = "H:mm:ss.SSS";
    private static final String FORMAT_PATTERN_WITH_MINUTES = "m:ss.SSS";
    private static final String FORMAT_PATTERN_WITH_SECONDS = "s.SSS";
    private static final String FORMAT_PATTERN_WITH_MILLISECS = "S";
    private static final long THRESHOLD_SECONDS = 1000;
    private static final long THRESHOLD_MINUTES = THRESHOLD_SECONDS * 60;
    private static final long THRESHOLD_HOURS = THRESHOLD_MINUTES * 60;

    private long startMillisecondClockValue;

    private StopWatch() {
        start();
    }

    public static StopWatch createAndStart() {
        return new StopWatch();
    }

    final public void start() {
        startMillisecondClockValue = millisecondClockValue();
    }

    private long millisecondClockValue() {
        return Clock.systemDefaultZone().millis();
    }

    public String stop() {
        return toString(Duration.ofMillis(millisecondClockValue() - startMillisecondClockValue));
    }

    private String toString(Duration duration) {
        long days = duration.toDays();
        long millis = duration.toMillis();
        String prefix = "";
        if (days > 0) {
            prefix = Long.toString(days) + (days == 1 ? " day " : " days ");
            millis = millis - duration.ofDays(days).toMillis();
        }
        Date date = new Date(millis - TimeZone.getDefault().getRawOffset());
        String pattern = resolvePattern(millis);
        FastDateFormat format = FastDateFormat.getInstance(pattern, Locale.getDefault());
        return prefix + format.format(date) + " [" + pattern + "]";
    }

    private String resolvePattern(long millis) {
        if (millis >= THRESHOLD_HOURS) {
            return FORMAT_PATTERN_WITH_HOURS;
        }
        if (millis >= THRESHOLD_MINUTES) {
            return FORMAT_PATTERN_WITH_MINUTES;
        }
        if (millis >= THRESHOLD_SECONDS) {
            return FORMAT_PATTERN_WITH_SECONDS;
        }
        return FORMAT_PATTERN_WITH_MILLISECS;
    }
}
