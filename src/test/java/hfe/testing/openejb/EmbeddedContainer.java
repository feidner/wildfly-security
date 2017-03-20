package hfe.testing.openejb;

import hfe.testing.OpenEjbTestNgListener;
import hfe.tools.StackTrace;
import hfe.tools.StopWatch;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.openejb.OpenEjbContainer;
import org.apache.openejb.config.ConfigurationFactory;
import org.apache.openejb.config.DeploymentFilterable;
import org.apache.openejb.config.FinderFactory;
import org.apache.openejb.loader.SystemInstance;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.ClasspathArchive;
import org.apache.xbean.finder.filter.Filter;
import org.apache.xbean.finder.filter.Filters;
import org.apache.xbean.finder.filter.IncludeExcludeFilter;
import org.testng.ITestNGListener;
import org.testng.annotations.Listeners;

import javax.ejb.embeddable.EJBContainer;
import javax.enterprise.inject.Instance;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

public class EmbeddedContainer {

    private static final String DEFAULT_EMBEDDED_DATABASE = "h2-tcp";
    private static final String DATA_SOURCE_SYSTEM_PROPERTY_KEY = "dataSource";
    private static final String EMBEDDED_DATABASE_FILENAME_SYSTEM_PROPERTY_KEY = "H2_FILE_NAME";


    private static final Set<String> EXCLUDES = Stream.of(
            ".*(Test|Mock)",
            ".*\\${1}.*"
    ).collect(Collectors.toSet());

    private final static Set<Class<?>> PRIMITIVE_TYPES = Stream.of(String.class, java.util.Date.class, BigDecimal.class, char.class, byte.class, int.class, short.class, long.class, boolean.class).collect(Collectors.toSet());

    private static final Set<String> INCLUDES = Stream.of("hfe.(beans).*").collect(Collectors.toSet());

    private static final Set<String> UI_EXCLUDES = Stream.of(
            ".*\\${1}.*"
    ).collect(Collectors.toSet());

    private static final Set<String> UI_INCLUDES = Stream.of(
            "hfe.ui.*"
    ).collect(Collectors.toSet());

    private static org.h2.tools.Server h2Server;
    private static EJBContainer container;
    private static final Logger log = Logger.getLogger(EmbeddedContainer.class.getSimpleName());

    private EmbeddedContainer() {
    }

    //@Test
    public void regexTestMitNegation() {
        String s1 = "D:/stfp/TeamCityInstallation/buildAgent/work/e86cd687cac42766/backend/target/classes";
        String s2 = "D:/stfp/TeamCityInstallation/buildAgent/work/e86cd687cac42766/backend/target/test-classes";
        String s3 = "D:/stfp/TeamCityInstallation/buildAgent/work/e86cd687cac42766/ui/ui-ulc-server/target/classes";
        String s4 = "stfp/backend-1.2.0-SNAPSHOT.jar";
        String s5 = "stfp/backend-1.2.0-SNAPSHOT-tests.jar";
        String s6 = "apache/commons-lang-2.3.4.jar";
        Filter includeFilter = Filters.patterns(".*(target/classes|backend).*");
        IncludeExcludeFilter filter = new IncludeExcludeFilter(includeFilter, Filters.patterns("(((.(?!SNAPSHOT))*|.*tests)\\.jar|.*test-classes)"));

        log.info(format("Accept %s: %s", s1, filter.accept(s1)));
        log.info(format("Accept %s: %s", s2, filter.accept(s2)));
        log.info(format("Accept %s: %s", s3, filter.accept(s3)));

        log.info(format("Accept %s: %s", s4, filter.accept(s4)));
        log.info(format("Accept %s: %s", s5, filter.accept(s5)));
        log.info(format("Accept %s: %s", s6, filter.accept(s6)));
    }

    public static void start(Object obj) {
        start(obj, (Runnable) null);
    }

    public static EJBContainer startForUi(Object obj, Set<String> definitly) {
        return startForUi(obj, new HashSet<>(), definitly);
    }

    public static EJBContainer startForUi(Object obj, Set<String> additionalCallers, Set<String> definitly) {
        log.info("Start EjbContainer fuer UI Test");
        Properties properties = PropertiesProvider.createFromStandalonXmlCheckedIn("h2-mem");

        properties.put("java:jboss/datasources/StfpDSNonJta.JdbcDriver", "");
        properties.put(DeploymentFilterable.CLASSPATH_INCLUDE, ".*(target/classes|SNAPSHOT).*");
        properties.put(DeploymentFilterable.CLASSPATH_EXCLUDE, "((.(?!SNAPSHOT))*|.*tests)\\.jar");

        additionalCallers.add(obj.getClass().getCanonicalName());

        createFilteredContainer(obj.getClass(), properties, additionalCallers, definitly, UI_INCLUDES, UI_EXCLUDES);
        applyCdiToObject(obj);
        return container;
    }

    public static void start(Object obj, Runnable runThisInContainer) {
        start(obj, SchemaGuard.CANCEL_PROCESS, runThisInContainer);
    }

    public static void start(Object obj, SchemaGuard schemaGuard) {
        start(obj, schemaGuard, null);
    }

    public static void start(Object obj, SchemaGuard schemaGuard, Runnable runThisInContainer) {
        ensureEjbContainerExists(obj, obj.getClass(), schemaGuard, runThisInContainer);
        if (runThisInContainer == null) {
            applyCdiToObject(obj);
        }
    }

    public static void start(Object obj, Class<?> testClass) {
        ensureEjbContainerExists(obj, testClass, SchemaGuard.CANCEL_PROCESS, null);
        applyCdiToObject(obj);
    }

    private static void ensureEjbContainerExists(Object obj, Class<?> classToTest, SchemaGuard schemaGuard, Runnable runThisInContainer) {

        if (container != null) {
            return;
        }

        String currentStacktrace = StackTrace.current();

        Set<String> callers = new HashSet<>();

        /*
         * hfe: Callers werden als ManagedBeans dem EjbContainer hinzugefuegt.
         * Der aktuelle Test muss als Caller hinzugefuegt werden, somit koennen die CDI-Annotationen aufgeloest werden.
         * Darueber hinaus muessen die TestRunner, die CDI nutzen auch als Caller hinzugefuegt werden.
         */
        callers.add(classToTest.getCanonicalName());
        callers.add(OpenEjbTestNgListener.class.getCanonicalName());

        boolean isIntellijJunitStarter = false, isIntellijJunitGroupStarter = false;
        if(StackTrace.currentMatches(".*org\\.junit", currentStacktrace)) {
            isIntellijJunitStarter = StackTrace.currentMatches(".*com\\.intellij\\.rt\\.execution\\.junit\\.JUnitStarter.*", currentStacktrace);
            isIntellijJunitGroupStarter = StackTrace.currentMatches(".*com\\.intellij\\.junit4\\.JUnit4TestRunnerUtil\\.buildRequest.*", currentStacktrace) ||
                    StackTrace.currentMatches(".*org\\.junit\\.runners\\.Suite\\.runChild.*", currentStacktrace);
        } else if(StackTrace.currentMatches(".*org\\.testng.*", currentStacktrace)) {
            isIntellijJunitStarter = true;
        } else {
            throw new RuntimeException("Testframework ist nicht bekannt!");
        }

        boolean isMainProgram = StackTrace.currentMatches(".*hfe\\..*main\\(.*", currentStacktrace);
        log.info(format("Test: %s, isIntellijJunitStarter: %s, isIntellijJunitGroupStarter: %s, isMainProgram: %s",
                classToTest.getSimpleName(), isIntellijJunitStarter, isIntellijJunitGroupStarter, isMainProgram));

        if (!isIntellijJunitGroupStarter && (isIntellijJunitStarter || isMainProgram)) {
            /*
             * Es wurde ein Test aus Intellij gestartet oder ein Programm ueber eine main-Methode
             */
            if (isMainProgram) {
                String dataSourceViaSystemProperty = System.getProperty(DATA_SOURCE_SYSTEM_PROPERTY_KEY);
                String embeddedDatabaseFilenameViaSystemProperties = System.getProperty(EMBEDDED_DATABASE_FILENAME_SYSTEM_PROPERTY_KEY);
                if (runThisInContainer == null || dataSourceViaSystemProperty != null || embeddedDatabaseFilenameViaSystemProperties != null) {
                    if("".equals(embeddedDatabaseFilenameViaSystemProperties)) {
                        PropertiesSelector.readH2Name(embeddedDatabaseFilenameViaUser -> createAndApplyContainer(PropertiesProvider.createFromStandalonXmlCheckedIn("h2-tcp", embeddedDatabaseFilenameViaUser), obj, classToTest, callers, runThisInContainer));
                    } else {
                        String name = dataSourceViaSystemProperty == null ? "h2-tcp" : dataSourceViaSystemProperty;
                        createAndApplyContainer(PropertiesProvider.createFromStandalonXmlCheckedIn(name, System.getProperty(EMBEDDED_DATABASE_FILENAME_SYSTEM_PROPERTY_KEY)), obj, classToTest, callers, runThisInContainer);
                    }
                } else {
                    PropertiesSelector.runWithSelectedProperties(properties -> createAndApplyContainer(properties, obj, classToTest, callers, runThisInContainer), null);
                }
            } else {
                Set<String> includes = new HashSet<>();
                Set<String> excludes = new HashSet<>();
                // hfe: Hier bin ich mir nicht sicher ob man alle Instanzen in rekursiven Feldern betrachten soll?
                boolean hasInstanceField = allNonStaticFields(classToTest).stream().anyMatch(f -> f.getType() == Instance.class);
                if (hasInstanceField) {
                    includes.add(".*");
                    excludes.addAll(EXCLUDES);
                } else {
                    includes.addAll(INCLUDES);
                    excludes.addAll(EXCLUDES);
                }
                Properties properties = PropertiesProvider.createFromStandalonXmlCheckedIn(DEFAULT_EMBEDDED_DATABASE);
                createFilteredContainer(classToTest, properties , callers, new HashSet<>(), includes, excludes);
            }
        } else {

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            File testClassPath = getTestClassPath(classLoader);
            Set<String> annotatedTests = getAnnotatedTests(testClassPath, classLoader);
            if (annotatedTests.contains(classToTest.getCanonicalName())) {
                callers.remove(classToTest.getCanonicalName());
                log.info(format("### ClasstoTest(%s) contained in Annotated-Tests", classToTest.getCanonicalName()));
            }

            Set<String> testsNeedEjbContainer = getTestsNeedEjbContainer(testClassPath);
            @SuppressWarnings("unchecked") Collection<String> testsNeededEjbContainerSkipAnnotatedTests = CollectionUtils.subtract(testsNeedEjbContainer, annotatedTests);
            log.info(format("### Tests not annotated but need Ejb-Container: %s", testsNeededEjbContainerSkipAnnotatedTests));

            callers.addAll(testsNeededEjbContainerSkipAnnotatedTests);

            Properties dataSourceProperties = manageDataSourceProperties(getOpenEjbDataSourceProperties(), callers);

            System.clearProperty(DeploymentFilterable.CLASSPATH_INCLUDE); // hfe: Hier wird keine Einschraenkung erlaubt, ist auch nicht noetig

            HfeFinderFactory.replaceScan(Stream.of(".*").collect(Collectors.toSet()), new HashSet<>(), new HashSet<>());

            createContainer(schemaGuard, dataSourceProperties);
        }
    }

    private static Properties manageDataSourceProperties(Properties dataSourceProperties, Set<String> callers) {
        dataSourceProperties.put(DeploymentFilterable.CLASSPATH_FILTER_DESCRIPTORS, Boolean.TRUE.toString());
        dataSourceProperties.put(FinderFactory.class.getCanonicalName(), HfeFinderFactory.class.getCanonicalName());
        dataSourceProperties.put(OpenEjbContainer.Provider.OPENEJB_ADDITIONNAL_CALLERS_KEY, StringUtils.join(callers, ","));
        return dataSourceProperties;
    }

    private static Properties getOpenEjbDataSourceProperties() {
        Properties properties;
        if (StringUtils.isEmpty(System.getProperty(DATA_SOURCE_SYSTEM_PROPERTY_KEY))) {
            properties = PropertiesProvider.createFromStandalonXmlCheckedIn(DEFAULT_EMBEDDED_DATABASE);
        } else {
            String propertyName = System.getProperty(DATA_SOURCE_SYSTEM_PROPERTY_KEY);
            properties = PropertiesProvider.createFromStandalonXmlCheckedIn(propertyName);
            assert properties != null : String.format("Diese Property-Datei liefert keine Properties: %s.properties", propertyName);
            log.info(format("### Genutzte Property-Datei: %s.properties", propertyName));
        }
        return properties;
    }

    private static void createFilteredContainer(Class<?> classToTest, Properties dataSourceProperties, Set<String> callers, Set<String> definitly, Set<String> includes, Set<String> excludes) {
        Map<Class<?>, Set<Field>> allAnnotations = findEjbs(classToTest);
        Set<Field> ejbAnnotated = allAnnotations.get(javax.ejb.EJB.class);
        if (!ejbAnnotated.isEmpty()) {
            definitly.add(cdiIncudes(ejbAnnotated));
            //ejbAnnotated.stream().filter(f -> f.getType() != TransactionBean.class).forEach(f -> callers.add(f.getType().getCanonicalName()));
            ejbAnnotated.forEach(f -> callers.add(f.getType().getCanonicalName()));
        }

        log.info(format("### Callers: %s", callers));

        Set<Field> injectAnnotated = allAnnotations.get(javax.inject.Inject.class);
        if (!injectAnnotated.isEmpty()) {
            definitly.add(cdiIncudes(injectAnnotated));
        }

        HfeFinderFactory.replaceScan(includes, definitly, excludes);

        dataSourceProperties = manageDataSourceProperties(dataSourceProperties, callers);

        createContainer(SchemaGuard.IGNORE_SCHEMA_GUARD, dataSourceProperties);
    }

    private static List<Field> allNonStaticFields(Class<?> clazz) {
        return FieldUtils.getAllFieldsList(clazz).stream().filter(field -> !Modifier.isStatic(field.getModifiers())).collect(Collectors.toList());
    }

    private static Map<Class<?>, Set<Field>> findEjbs(Class<?> toTest) {
        Map<Class<?>, Set<Field>> annotatetdClasses = new HashMap<>();
        annotatetdClasses.put(javax.ejb.EJB.class, new HashSet<>());
        annotatetdClasses.put(javax.inject.Inject.class, new HashSet<>());
        List<Field> allNonStaticFields = allNonStaticFields(toTest);
        findEjbs(annotatetdClasses, allNonStaticFields, new HashSet<>());
        return annotatetdClasses;
    }

    private static void findEjbs(Map<Class<?>, Set<Field>> result, List<Field> injectAnnotated, Set<Class<?>> alreadyAnalyzed) {
        injectAnnotated.forEach(field -> {
            Class<?> fieldClass = field.getType();
            if (!PRIMITIVE_TYPES.contains(fieldClass)) {
                try {
                    Class<?> clazz = Class.forName(fieldClass.getCanonicalName());
                    alreadyAnalyzed.add(clazz);

                    if (field.isAnnotationPresent(javax.ejb.EJB.class)) {
                        result.get(javax.ejb.EJB.class).add(field);
                    }
                    if (field.isAnnotationPresent(javax.inject.Inject.class)) {
                        result.get(javax.inject.Inject.class).add(field);
                    }
                    List<Field> allFields = allNonStaticFields(clazz);

                    List<Field> ejbFields = allFields.stream().filter(f -> !alreadyAnalyzed.contains(f.getType()) &&
                            f.isAnnotationPresent(javax.ejb.EJB.class)).collect(Collectors.toList());
                    if (!ejbFields.isEmpty()) {
                        findEjbs(result, ejbFields, alreadyAnalyzed);
                        result.get(javax.ejb.EJB.class).addAll(ejbFields);
                    }
                    List<Field> injectFields = allFields.stream().filter(f -> !alreadyAnalyzed.contains(f.getType()) &&
                            f.isAnnotationPresent(javax.inject.Inject.class)).collect(Collectors.toList());
                    if (!injectFields.isEmpty()) {
                        findEjbs(result, injectFields, alreadyAnalyzed);
                        result.get(javax.inject.Inject.class).addAll(injectFields);
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    private static void findFieldTypeInstance(Set<Field> instanceFields, List<Field> injectAnnotated, Set<Class<?>> already) {
        injectAnnotated.forEach(field -> {
            try {
                Class<?> clazz = Class.forName(field.getType().getCanonicalName());
                already.add(clazz);

                List<Field> allFields = allNonStaticFields(clazz);
                allFields.stream().filter(f -> f.getType() == Instance.class).forEach(instanceFields::add);
                List<Field> injectedFields = allFields.stream().filter(f -> !already.contains(f.getType()) &&
                        (f.isAnnotationPresent(javax.inject.Inject.class) || f.isAnnotationPresent(javax.ejb.EJB.class))).collect(Collectors.toList());
                if (!injectedFields.isEmpty()) {
                    findFieldTypeInstance(instanceFields, injectedFields, already);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }


    private static void createAndApplyContainer(Properties properties, Object obj, Class<?> classToTest, Set<String> callers, Runnable runThisInContainer) {
        startH2(properties);
        try {
            Set<String> includes = Stream.of(".*").collect(Collectors.toSet());
            Set<String> excludes = new HashSet<>(EXCLUDES);
            for (Object key : properties.keySet()) {
                log.fine(format("## %s = %s", key, properties.get(key)));
            }
            createFilteredContainer(classToTest, properties, callers, new HashSet<>(), includes, excludes);
            applyCdiToObject(obj);
            if (runThisInContainer != null) {
                runThisInContainer.run();
            }
        } finally {
            stopH2();
        }
    }

    private static String cdiIncudes(Set<Field> fields) {
        String injects = fields.stream().
                map(f -> f.getType().getSimpleName()).
                collect(Collectors.toSet()). // hfe: Dies wird gemacht um keine doppelten Eintraege zu haben
                stream().
                reduce((s1, s2) -> format("%s|%s", s1, s2)).
                get();
        return format(".*(%s)", injects);
    }

    private static File getTestClassPath(ClassLoader classLoader) {
        SystemInstance.get().setProperty(DeploymentFilterable.CLASSPATH_INCLUDE, ".*(test/classes|test-classes).*");
        SystemInstance.get().setProperty(DeploymentFilterable.CLASSPATH_FILTER_DESCRIPTORS, Boolean.TRUE.toString());
        List<File> modules = new ConfigurationFactory().getModulesFromClassPath(null, classLoader);
        assert modules.size() == 1 : "Es muss genau ein Pfad mit Testklassen vorliegen";
        File testClassPath = modules.get(0);
        log.info(format("### TestClassPath: %s", testClassPath.getAbsolutePath()));
        return testClassPath;
    }

    private static Set<String> getAnnotatedTests(File testClassPath, ClassLoader classLoader) {
        StopWatch st = StopWatch.createAndStart();
        try {
            AnnotationFinder finder = new AnnotationFinder(ClasspathArchive.archive(classLoader, testClassPath.toURI().toURL()));
            Set<String> annotatedTests = finder.findAnnotatedClasses(javax.annotation.ManagedBean.class).stream().map(Class::getCanonicalName).collect(Collectors.toSet());
            log.info(format("### AnnotationFinder for Tests = %s", st.stop()));
            return annotatedTests;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<String> getClassesMatches(File classPath, String regex) {
        StopWatch st = StopWatch.createAndStart();
        Collection<File> files = FileUtils.listFiles(classPath, new String[]{"class"}, true);
        Set<File> runningTests = files.stream().filter(f -> !f.getName().contains("$") && f.getName().matches(regex)).collect(Collectors.toSet());
        log.info(format("### Zeit fuer Klassensuche: %s, Pfad: %s, Regex: %s", st.stop(), classPath, regex));
        return runningTests.stream().map(f -> FilenameUtils.getBaseName(f.getPath().substring(classPath.getPath().length() + 1).replaceAll("\\" + File.separator, "."))).collect(Collectors.toSet());
    }

    private static Set<String> getTestsNeedEjbContainer(File testClassPath) {
        Set<String> classNames = getClassesMatches(testClassPath, ".*(TestNightly|TestMigration|Test|Importer).class");
        /*
        st.start();
        Set<File> containerBaseTests = runningTests.stream().filter(f -> {
            try {
                String newPath = f.getPath().replace("target", "src" + File.separator + "test");
                newPath = newPath.replace("test-classes", "java");
                newPath = newPath.replace("class", "java");
                List<String> content = FileUtils.readLines(new File(newPath));
                if (content.stream().anyMatch(line -> line.matches(".*(AbstractAdministrationFacadeTest|AbstractSearchTest|AbstractFolderDaoTest|TestEjbContainer|TransactionBasedTestRunner|TransactionDbSetupTestRunner|InjectionBasedTest).*"))) {
                    return true;
                }
                return false;
            } catch (IOException e) {
                throw Reject.developmentError(e);
            }
        }).collect(Collectors.toSet());
        Set<String> fileTests = containerBaseTests.stream().map(f -> FilenameUtils.getBaseName(f.getPath().substring(classPath.getPath().length() + 1).replaceAll("\\" + File.separator, "."))).collect(Collectors.toSet());
        log.info(String.format("### StringSearch for Tests = %s", st.stop()));
        */
        StopWatch st = StopWatch.createAndStart();
        Set<String> testsNeedEjbContainer = classNames.stream().filter(s -> {
            try {
                Class<?> cl = Class.forName(s);
                if (!Modifier.isAbstract(cl.getModifiers())) {
                    /*
                    if (cl.isAnnotationPresent(RunWith.class)) {
                        RunWith anno = cl.getAnnotation(RunWith.class);
                        Class<?> val = anno.value();
                        if (val == TransactionBasedTestRunner.class || val == TransactionDbSetupTestRunner.class) {
                            return true;
                        }
                    } else*/
                    if (cl.isAnnotationPresent(Listeners.class)) {
                        Listeners anno = cl.getAnnotation(Listeners.class);
                        Class<? extends ITestNGListener>[] val = anno.value();
                        if (val.length == 1 && val[0] == OpenEjbTestNgListener.class) {
                            return true;
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            return false;
        }).collect(Collectors.toSet());
        log.info(format("### ClassSearch for Tests = %s", st.stop()));
        return testsNeedEjbContainer;
    }

    private static void createContainer(SchemaGuard schemaGuard, Properties dataSourceProperties) {

        // fuer JAXB ausserhalb vom JBoss: z.B. im CissWebserviceClient
        System.setProperty("javax.xml.bind.JAXBContext", "com.sun.xml.internal.bind.v2.ContextFactory");
        System.setProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize", "true");

        // kein Caching bei Tests (ueberschreibt Werte aus persistence.xml)
        System.setProperty("hibernate.cache.use_second_level_cache", "false");
        System.setProperty("hibernate.cache.use_query_cache", "false");

        // Hier fuegen wir einen eigenen Scann fuer das Hibernateframework hinzu
        //System.setProperty(AvailableSettings.SCANNER, StfpHibernateScanner.class.getCanonicalName());

        if (!dataSourceProperties.containsKey(DeploymentFilterable.CLASSPATH_INCLUDE)) {
            dataSourceProperties.put(DeploymentFilterable.CLASSPATH_INCLUDE, ".*classes.*");
        }
        if (!dataSourceProperties.containsKey(DeploymentFilterable.CLASSPATH_EXCLUDE)) {
            dataSourceProperties.put(DeploymentFilterable.CLASSPATH_EXCLUDE, ".*jar");
        }

        try {

            StopWatch watch = StopWatch.createAndStart();
            EJBContainer ejbContainer = EJBContainer.createEJBContainer(dataSourceProperties);
            log.info(format("### ContainerBuildTime: %s", watch.stop()));
            container = ejbContainer;
        } catch (Exception e) {
            log.log(Level.SEVERE, "OpenEjb-Container konnte nicht gestartet werden", e);
            System.exit(1); // hartes System.exit(1), sonst wird das fuer jeden einzelnen Test erneut durchgefuehrt, was zeitaufwaendig und nutzlos ist
        }

        checkSchemaGuard(schemaGuard);
    }

    private static void checkSchemaGuard(SchemaGuard schemaGuard) {
        if (schemaGuard == null || schemaGuard == SchemaGuard.CANCEL_PROCESS) {
            try {
                List<String> guardNames = executeSqlSelect(null, "SELECT NAME FROM SCHEMA_GUARD");
                if (!guardNames.isEmpty()) {
                    throw new RuntimeException("Ein oder mehrere Datenbank-Waechter sind gesetzt:\n" + StringUtils.join(guardNames, "    \n"));
                }
            } catch (SQLException ignore) {
                // kein guard tabelle
            }
        }
    }


    private static void startH2(Properties properties) {
        if(properties.containsKey(PropertiesProvider.EMBEDDED_H2)) {
            try {
                h2Server = org.h2.tools.Server.createTcpServer().start();
                Class.forName("org.h2.Driver");
                String dbUrl = properties.getProperty((String) properties.keySet().stream().filter(obj ->
                        obj.toString().contains("JdbcUrl")).findAny().get());
                DriverManager.getConnection(dbUrl, "sa", "sa");
            } catch (SQLException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private static void stopH2() {
        if(h2Server != null) {
            h2Server.shutdown();
            h2Server.stop();
        }
    }

    public static <T> T applyCdiToObject(T obj) {
        try {
            container.getContext().bind("inject", obj);
            return obj;
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void closeContainer() {
        if (container != null) {
            container.close();
            container = null;
        }
    }

    private static List<String> executeSqlSelect(DataSource dataSource, String currentSqlQuery) throws SQLException {
        Statement statement = dataSource.getConnection().createStatement();
        statement.execute(currentSqlQuery);
        ResultSet rs = statement.getResultSet();
        List<String> result = new ArrayList<>();
        while(rs.next()) {
            result.add(rs.getString(1));
        }
        rs.close();
        statement.close();
        return result;
    }

    private enum SchemaGuard {
        CANCEL_PROCESS, IGNORE_SCHEMA_GUARD;
    }
}
