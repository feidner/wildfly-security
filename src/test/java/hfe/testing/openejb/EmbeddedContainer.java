package hfe.testing.openejb;

import hfe.testing.OpenEjbTransactionNgListener;
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
import org.apache.xbean.finder.filter.Filter;
import org.apache.xbean.finder.filter.Filters;
import org.apache.xbean.finder.filter.IncludeExcludeFilter;
import org.testng.ITestNGListener;
import org.testng.annotations.Listeners;

import javax.ejb.embeddable.EJBContainer;
import javax.enterprise.inject.Instance;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
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

    private static final String DEFAULT_DATASOURCE = "h2-tcp";
    private static final String DEFAULT_MAIN_PROGRAM_DATASOURCE = "h2-tcp";
    private static final String DATA_SOURCE_SYSTEM_PROPERTY_KEY = "datasource";
    private static final String EMBEDDED_DATABASE_FILENAME_SYSTEM_PROPERTY_KEY = "H2_FILE_NAME";


    private static final Set<String> EXCLUDES = Collections.unmodifiableSet(Stream.of(
            ".*(Test|Mock)",
            ".*\\${1}.*"
    ).collect(Collectors.toSet()));

    private final static Set<Class<?>> PRIMITIVE_TYPES = Stream.of(String.class, java.util.Date.class, BigDecimal.class, char.class, byte.class, int.class, short.class, long.class, boolean.class).collect(Collectors.toSet());

    private static final Set<String> INCLUDES = Collections.unmodifiableSet(Stream.of("hfe.(beans).*").collect(Collectors.toSet()));

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


    public static EJBContainer startForUi(Object obj, Set<String> definitly) {
        log.info("Start EjbContainer fuer UI Test");
        Properties properties = PropertiesProvider.createFromStandalonXmlCheckedIn(DEFAULT_DATASOURCE);
        properties.put(DeploymentFilterable.CLASSPATH_INCLUDE, ".*(target/classes|SNAPSHOT).*");
        properties.put(DeploymentFilterable.CLASSPATH_EXCLUDE, "((.(?!SNAPSHOT))*|.*tests)\\.jar");
        createFilteredContainer(obj.getClass(), properties, new HashSet<>(), definitly, UI_INCLUDES, UI_EXCLUDES);
        applyCdiToObject(obj);
        return container;
    }

    public static void startMain(Object obj, Runnable runThisInContainer) {
        log.info("Start EjbContainer fuer MainProgramm");
        String dataSourceViaSystemProperty = System.getProperty(DATA_SOURCE_SYSTEM_PROPERTY_KEY);
        String embeddedDatabaseFilenameViaSystemProperties = System.getProperty(EMBEDDED_DATABASE_FILENAME_SYSTEM_PROPERTY_KEY);
        Class<?> classToTest = obj.getClass();
        if (runThisInContainer == null || dataSourceViaSystemProperty != null || embeddedDatabaseFilenameViaSystemProperties != null) {
            if("".equals(embeddedDatabaseFilenameViaSystemProperties)) {
                PropertiesSelector.readH2Name(embeddedDatabaseFilenameViaUser -> createAndApplyContainer(PropertiesProvider.createFromStandalonXmlCheckedIn(DEFAULT_MAIN_PROGRAM_DATASOURCE, embeddedDatabaseFilenameViaUser), obj, classToTest, runThisInContainer));
            } else {
                String name = dataSourceViaSystemProperty == null ? DEFAULT_MAIN_PROGRAM_DATASOURCE : dataSourceViaSystemProperty;
                Properties fromStandaloneProperties = PropertiesProvider.createFromStandalonXmlCheckedIn(name, System.getProperty(EMBEDDED_DATABASE_FILENAME_SYSTEM_PROPERTY_KEY));
                createAndApplyContainer(fromStandaloneProperties, obj, classToTest, runThisInContainer);
            }
        } else {
            PropertiesSelector.runWithSelectedProperties(properties -> createAndApplyContainer(properties, obj, classToTest, runThisInContainer), null);
        }
    }

    public static void start(Object obj, Class<?> testClass, Set<String> callers, Set<String> excludes) {
        ensureEjbContainerExists(testClass, SchemaGuard.CANCEL_PROCESS, callers, excludes);
        applyCdiToObject(obj);
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

    public static <T> Set<Class<T>> collectContextClasses(String regex) throws NamingException, ClassNotFoundException {
        Set<Class<T>> classes = new HashSet<>();
        NamingEnumeration<NameClassPair> enumeration = null;
        try {
            enumeration = container.getContext().list("java:global/backend");
        } catch (NamingException e) {
            enumeration = container.getContext().list("java:global/backend-1.2.0-SNAPSHOT");
        }
        while (enumeration.hasMoreElements()) {
            NameClassPair pair = enumeration.next();
            if (pair.getName().matches(regex)) {
                @SuppressWarnings("unchecked") Class<T> clazz = (Class<T>) Class.forName(pair.getName().substring(pair.getName().indexOf("!") + 1));
                //Object obj = container.getContext().lookup("java:global/backend/" + pair.getName());
                classes.add(clazz);
            }
        }
        return classes;
    }

    // ######################## PRIVATES #####################

    private static void ensureEjbContainerExists(Class<?> classToTest, SchemaGuard schemaGuard, Set<String> callers, Set<String> excludes) {

        if (container != null) {
            return;
        }
        String currentStacktrace = StackTrace.current();
        boolean isIntellijJunitStarter, isIntellijJunitGroupStarter = false;
        if(StackTrace.currentMatches(".*org\\.junit", currentStacktrace)) {
            isIntellijJunitStarter = StackTrace.currentMatches(".*com\\.intellij\\.rt\\.execution\\.junit\\.JUnitStarter.*", currentStacktrace);
            isIntellijJunitGroupStarter = StackTrace.currentMatches(".*com\\.intellij\\.junit4\\.JUnit4TestRunnerUtil\\.buildRequest.*", currentStacktrace) ||
                    StackTrace.currentMatches(".*org\\.junit\\.runners\\.Suite\\.runChild.*", currentStacktrace);
        } else if(StackTrace.currentMatches(".*org\\.testng.*", currentStacktrace)) {
            isIntellijJunitStarter = true;
        } else {
            throw new RuntimeException("Testframework ist nicht bekannt!");
        }

        log.info(format("Test: %s, isIntellijJunitStarter: %s, isIntellijJunitGroupStarter: %s",
                classToTest.getSimpleName(), isIntellijJunitStarter, isIntellijJunitGroupStarter));

        if (!isIntellijJunitGroupStarter && isIntellijJunitStarter) {
            createFilteredContainer(classToTest, getOpenEjbDataSourceProperties(), callers,
                    new HashSet<>(), INCLUDES, CollectionUtils.union(excludes, EXCLUDES));
        } else {
            File testClassPath = getTestClassPath(Thread.currentThread().getContextClassLoader());
            Set<String> testsNeedEjbContainer = getTestsNeedEjbContainer(testClassPath);
            log.info(format("### Tests not annotated but need Ejb-Container: %s", testsNeedEjbContainer));
            callers.addAll(testsNeedEjbContainer);

            Properties dataSourceProperties = manageDataSourceProperties(getOpenEjbDataSourceProperties(), callers);

            System.clearProperty(DeploymentFilterable.CLASSPATH_INCLUDE); // hfe: Hier wird keine Einschraenkung erlaubt, ist auch nicht noetig

            HfeFinderFactory.replaceScan(Stream.of(".*").collect(Collectors.toSet()), new HashSet<>(), new HashSet<>());

            createContainer(schemaGuard, dataSourceProperties);
        }
    }

    private static boolean classToTestHasInstanceFields(Class<?> classToTest) {
        // hfe: Hier bin ich mir nicht sicher ob man alle Instanzen in rekursiven Feldern betrachten soll?
        return allNonStaticFields(classToTest).stream().anyMatch(f -> f.getType() == Instance.class);
    }

    private static Properties manageDataSourceProperties(Properties dataSourceProperties, Set<String> callers) {
        dataSourceProperties.put(DeploymentFilterable.CLASSPATH_FILTER_DESCRIPTORS, Boolean.TRUE.toString());
        dataSourceProperties.put(FinderFactory.class.getTypeName(), HfeFinderFactory.class.getTypeName());
        dataSourceProperties.put(OpenEjbContainer.Provider.OPENEJB_ADDITIONNAL_CALLERS_KEY, StringUtils.join(callers, ","));
        dataSourceProperties.put("xbean.finder.use.get-resources", Boolean.TRUE.toString());
        return dataSourceProperties;
    }

    private static Properties getOpenEjbDataSourceProperties() {
        Properties properties;
        if (StringUtils.isEmpty(System.getProperty(DATA_SOURCE_SYSTEM_PROPERTY_KEY))) {
            properties = PropertiesProvider.createFromStandalonXmlCheckedIn(DEFAULT_DATASOURCE);
        } else {
            String propertyName = System.getProperty(DATA_SOURCE_SYSTEM_PROPERTY_KEY);
            properties = PropertiesProvider.createFromStandalonXmlCheckedIn(propertyName);
            assert properties != null : String.format("Diese Property-Datei liefert keine Properties: %s.properties", propertyName);
            log.info(format("### Genutzte Property-Datei: %s.properties", propertyName));
        }
        return properties;
    }

    private static boolean isTestClass(Field f) {
        if(f.getType().getSimpleName().endsWith("Test")) {
            return true;
        }
        Class<?> clazz = f.getType().getEnclosingClass();
        while(clazz != null) {
            if(clazz.getSimpleName().endsWith("Test")) {
                return true;
            }
            clazz = clazz.getEnclosingClass();
        }
        return false;
    }

    private static void createFilteredContainer(Class<?> classToTest, Properties dataSourceProperties, Set<String> callers, Set<String> definitly, Set<String> includes, Collection<String> excludes) {

        if(!callers.contains(classToTest.getTypeName())) {
            callers.add(classToTest.getTypeName());
        }

        includes = classToTestHasInstanceFields(classToTest) ? Stream.of(".*").collect(Collectors.toSet()) : includes;

        Map<Class<?>, Set<Field>> allAnnotations = findCDIs(classToTest);
        Set<Field> ejbAnnotated = allAnnotations.get(javax.ejb.EJB.class);
        if (!ejbAnnotated.isEmpty()) {
            definitly.add(cdiIncudes(ejbAnnotated));
            // die ejb annotated klassen den callers hinzufuegen
            ejbAnnotated.forEach(f -> callers.add(f.getType().getTypeName()));
        }

        log.info(format("### Callers: %s", callers));

        Set<Field> injectAnnotated = allAnnotations.get(javax.inject.Inject.class);
        if (!injectAnnotated.isEmpty()) {
            definitly.add(cdiIncudes(injectAnnotated));
            injectAnnotated.stream().
                    filter(f -> isTestClass(f)).
                    forEach(f -> callers.add(f.getType().getTypeName()));
        }

        HfeFinderFactory.replaceScan(includes, definitly, excludes);

        dataSourceProperties = manageDataSourceProperties(dataSourceProperties, callers);

        createContainer(SchemaGuard.IGNORE_SCHEMA_GUARD, dataSourceProperties);
    }

    private static List<Field> allNonStaticFields(Class<?> clazz) {
        return FieldUtils.getAllFieldsList(clazz).stream().filter(field -> !Modifier.isStatic(field.getModifiers())).collect(Collectors.toList());
    }

    private static Map<Class<?>, Set<Field>> findCDIs(Class<?> toTest) {
        Map<Class<?>, Set<Field>> annotatetdClasses = new HashMap<>();
        annotatetdClasses.put(javax.ejb.EJB.class, new HashSet<>());
        annotatetdClasses.put(javax.inject.Inject.class, new HashSet<>());
        List<Field> allNonStaticFields = allNonStaticFields(toTest);
        findCDIs(annotatetdClasses, allNonStaticFields, new HashSet<>());
        return annotatetdClasses;
    }

    private static void findCDIs(Map<Class<?>, Set<Field>> result, List<Field> injectAnnotated, Set<Class<?>> alreadyAnalyzed) {
        injectAnnotated.forEach(field -> {
            Class<?> fieldClass = field.getType();
            if (!PRIMITIVE_TYPES.contains(fieldClass)) {
                try {
                    Class<?> clazz = Class.forName(fieldClass.getTypeName());
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
                        findCDIs(result, ejbFields, alreadyAnalyzed);
                        result.get(javax.ejb.EJB.class).addAll(ejbFields);
                    }
                    List<Field> injectFields = allFields.stream().filter(f -> !alreadyAnalyzed.contains(f.getType()) &&
                            f.isAnnotationPresent(javax.inject.Inject.class)).collect(Collectors.toList());
                    if (!injectFields.isEmpty()) {
                        findCDIs(result, injectFields, alreadyAnalyzed);
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


    private static void createAndApplyContainer(Properties properties, Object obj, Class<?> classToTest, Runnable runThisInContainer) {
        startH2(properties);
        try {
            Set<String> includes = Stream.of(".*").collect(Collectors.toSet());
            Set<String> excludes = new HashSet<>(EXCLUDES);
            for (Object key : properties.keySet()) {
                log.fine(format("## %s = %s", key, properties.get(key)));
            }
            createFilteredContainer(classToTest, properties, new HashSet<>(), new HashSet<>(), includes, excludes);
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
                        if (val.length == 1 && val[0] == OpenEjbTransactionNgListener.class) {
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
            dataSourceProperties.put(DeploymentFilterable.CLASSPATH_INCLUDE, ".*classes/main.*");
        }
        if (!dataSourceProperties.containsKey(DeploymentFilterable.CLASSPATH_EXCLUDE)) {
            dataSourceProperties.put(DeploymentFilterable.CLASSPATH_EXCLUDE, ".*jar");
        }

        try {
            /*
            log.info("### StfpDS.JdbcUrl: " + getDataSourceProperty(dataSourceProperties, DatasourceName.Stfp, "JdbcUrl"));
            log.info("### StfpDS.Username: " + getDataSourceProperty(dataSourceProperties, DatasourceName.Stfp, "Username"));

            log.info("### GreatDS.JdbcUrl: " + getDataSourceProperty(dataSourceProperties, DatasourceName.Great, "JdbcUrl"));
            log.info("### GreatDS.Username: " + getDataSourceProperty(dataSourceProperties, DatasourceName.Great, "Username"));

            log.info("### TradeFinance.JdbcUrl: " + getDataSourceProperty(dataSourceProperties, DatasourceName.TradeFinance, "JdbcUrl"));
            log.info("### TradeFinance.Username: " + getDataSourceProperty(dataSourceProperties, DatasourceName.TradeFinance, "Username"));
            */

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
