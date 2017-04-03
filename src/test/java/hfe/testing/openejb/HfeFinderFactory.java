package hfe.testing.openejb;

import hfe.tools.StopWatch;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.openejb.config.DeploymentModule;
import org.apache.openejb.config.FinderFactory;
import org.apache.openejb.config.Module;
import org.apache.openejb.util.Saxs;
import org.apache.xbean.finder.Annotated;
import org.apache.xbean.finder.IAnnotationFinder;
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.archive.ClassesArchive;
import org.apache.xbean.finder.archive.ClasspathArchive;
import org.apache.xbean.finder.archive.FilteredArchive;
import org.apache.xbean.finder.filter.Filter;
import org.apache.xbean.finder.filter.FilterList;
import org.apache.xbean.finder.filter.PatternFilter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.xml.parsers.SAXParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HfeFinderFactory extends FinderFactory {
    private static final String FINDER_XML = "FINDER_XML";
    private StopWatch watch = StopWatch.createAndStart();
    private Filter filter;

    public HfeFinderFactory() {
        StfpFinderXmlScanner scanner = read();
        Logger.getLogger(HfeFinderFactory.class.getSimpleName()).info(String.format("### Dateinamen-includes: %s", scanner.getIncludes()));
        Logger.getLogger(HfeFinderFactory.class.getSimpleName()).info(String.format("### Dateinamen-definitly: %s", scanner.getDefinitlyIncludes()));
        Logger.getLogger(HfeFinderFactory.class.getSimpleName()).info(String.format("### Dateinamen-excludes: %s", scanner.getExcludes()));
        this.filter = scanner.getFilter();
    }

    @Override
    public IAnnotationFinder create(DeploymentModule module) throws Exception {
        IAnnotationFinder finder;
        if (module.getJarLocation() != null) {
            final String location = module.getJarLocation();
            final File file = new File(location);
            URL url = file.toURI().toURL();
            StfpConfigureableClasspathArchive archive = new StfpConfigureableClasspathArchive((Module) module, filter, url);
            watch.start();
            StfpAnnotationFinder annotationFinder = new StfpAnnotationFinder(archive);

            Logger.getLogger(HfeFinderFactory.class.getSimpleName()).info(String.format("### %s: %s hat gebraucht %s, jar: %s", ((Module) module).getUniqueId(),
                    annotationFinder.getClass().getSimpleName(), watch.stop(), module.getJarLocation()));

            annotationFinder.link();
            finder = annotationFinder;
        } else {
            OpenEJBAnnotationFinder openEJBAnnotationFinder = new OpenEJBAnnotationFinder(new ClassesArchive(ensureMinimalClasses(module)));
            openEJBAnnotationFinder.enableMetaAnnotations();
            finder = openEJBAnnotationFinder;
        }
        return finder;
    }

    private static String instaString(Collection<String> strings, String toReplace) {
        return StringUtils.join(strings.stream().map(s -> String.format(toReplace, s)).collect(Collectors.toSet()), "\n");
    }

    public static void replaceScan(Collection<String> includes, Collection<String> defintitlyIncludes, Collection<String> excludes) {
        String includeString = instaString(includes, "<include>%s</include>");
        String definitlyIncludeString = instaString(defintitlyIncludes, "<definitlyInclude>%s</definitlyInclude>");
        String excludeString = instaString(excludes, "<exclude>%s</exclude>");
        String xml = String.format("<?xml version=\"1.0\"?>\n" +
                "<scan>\n" +
                "    <includes>\n" +
                "        %s\n" +
                "    </includes>\n" +
                "    <definitlyIncludes>\n" +
                "        %s\n" +
                "    </definitlyIncludes>\n" +
                "    <excludes>\n" +
                "        %s\n" +
                "    </excludes>\n" +
                "</scan>", includeString, definitlyIncludeString, excludeString);
        System.getProperties().put(FINDER_XML, xml);
    }

    public static StfpFinderXmlScanner read() {
        String xmlString = System.getProperties().getProperty(FINDER_XML);
        try {
            final SAXParser parser = Saxs.factory().newSAXParser();
            final StfpFinderXmlScanner handler = new StfpFinderXmlScanner();
            parser.parse(IOUtils.toInputStream(xmlString), handler);
            return handler;
        } catch (final Exception e) {
            throw new RuntimeException("can't parse " + xmlString);
        }
    }

    private static class StfpAnnotationFinder extends OpenEJBAnnotationFinder {
        StfpAnnotationFinder(StfpConfigureableClasspathArchive archive) {
            super(archive);
        }

        @Override
        public List<Annotated<Class<?>>> findMetaAnnotatedClasses(Class<? extends Annotation> annotation) {
            if(annotation == Singleton.class) {
                List<Annotated<Class<?>>> classes = super.findMetaAnnotatedClasses(annotation);
                Set<Annotated<Class<?>>> classesWithStartupAnnotation = classes.stream().filter(clazz -> clazz.isAnnotationPresent(Startup.class)).collect(Collectors.toSet());
                //classes.removeAll(classesWithStartupAnnotation);
                return classes;
            }
            return super.findMetaAnnotatedClasses(annotation);
        }

        @Override
        protected void readClassDef(String className) {
            super.readClassDef(className);
        }

        @Override
        protected void readClassDef(InputStream in) throws IOException {
            super.readClassDef(in);
        }
    }

    private static class StfpConfigureableClasspathArchive implements Archive {

        private FilteredArchive archive;
        private Archive fileArchive;

        StfpConfigureableClasspathArchive(Module module, Filter filter, URL url) {
            this.fileArchive = archive(module.getClassLoader(), url);
            this.archive = archive(fileArchive, filter);
        }

        private static FilteredArchive archive(Archive archive, Filter filter) {
            return new FilteredArchive(archive, filter);
        }

        private static Archive archive(ClassLoader loader, URL url) {
            return ClasspathArchive.archive(loader, url);
        }

        @Override
        public InputStream getBytecode(String className) throws IOException, ClassNotFoundException {
            return archive.getBytecode(className);
        }

        @Override
        public Class<?> loadClass(String className) throws ClassNotFoundException {
            return archive.loadClass(className);
        }

        @Override
        public Iterator<Entry> iterator() {
            return archive.iterator();
        }
    }

    private static class StfpFinderXmlScanner extends DefaultHandler {
        private final Set<String> includes = new HashSet<>();
        private final Set<String> definitlyIncludes = new HashSet<>();
        private final Set<String> excludes = new HashSet<>();
        private Set<String> current;

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
            if ("include".equals(qName)) {
                current = includes;
            } else if ("definitlyInclude".equals(qName)) {
                current = definitlyIncludes;
            } else if ("exclude".equals(qName)) {
                current = excludes;
            }
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            if (current != null && length > 1) {
                current.add(new String(ch, start, length));
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            current = null;
        }

        Set<String> getIncludes() {
            return includes;
        }

        Set<String> getExcludes() {
            return excludes;
        }

        Set<String> getDefinitlyIncludes() {
            return definitlyIncludes;
        }

        public Filter getFilter() {
            return new IncludeExcludeFilter(getIncludes(), getDefinitlyIncludes(), getExcludes());
        }
    }

    private static class IncludeExcludeFilter implements Filter {

        private FilterList includes, definitlyIncludes, excludes;

        IncludeExcludeFilter(Set<String> includes, Set<String> definitlyIncludes, Set<String> excludes) {
            this.includes = getFilterList(includes);
            this.excludes = getFilterList(excludes);
            this.definitlyIncludes = getFilterList(definitlyIncludes);

        }
        @Override
        public boolean accept(String name) {
            boolean accept = false;
            if(includes.getFilters().isEmpty() || includes.accept(name)) {
                if(definitlyIncludes.accept(name)) {
                    accept = true;
                }
                if(excludes.getFilters().isEmpty() || !excludes.accept(name)) {
                    accept = true;
                }
            }
            return accept;
        }

        private static FilterList getFilterList(Set<String> list) {
            return new FilterList(list.stream().map(PatternFilter::new).collect(Collectors.toList()));
        }
    }



}
