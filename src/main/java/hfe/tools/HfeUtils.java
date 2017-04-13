package hfe.tools;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class HfeUtils {

    //StreamSupport.stream(Spliterators.spliteratorUnknownSize(dropDatabases.iterator(), 0), false).forEach(drop -> drop.drop(connection));

    private HfeUtils() {

    }

    public static URL getClassesFolderURL() throws IOException {
        List<URL> resources = Collections.list(HfeUtils.class.getClassLoader().getResources("."));
        URL classesURL = resources.stream().filter(url -> url.getPath().contains("classes") && !url.getPath().contains("test")).findAny().orElseThrow(() -> new RuntimeException("Verzeichnis classes kann nicht gefunden werden"));
        return classesURL;
    }

    public static Class<?> getTopLevelEnclosingClass(Class<?> clazz) {
        while (clazz.getEnclosingClass() != null) {
            clazz = clazz.getEnclosingClass();
        }
        return clazz;
    }
}
