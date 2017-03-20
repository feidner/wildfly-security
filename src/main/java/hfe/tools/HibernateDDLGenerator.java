package hfe.tools;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.internal.MetadataBuilderImpl;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaExport;

import javax.naming.NamingException;
import javax.persistence.Entity;
import javax.sql.DataSource;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

public class HibernateDDLGenerator {

    private static List<Class<?>> findEntityClasses(URL dir) {
        return FileUtils.listFiles(new File(dir.getPath()), new String[]{"class"}, true).stream()
                .map(File::getPath)
                .map(className -> extractClassNameFromFileName(dir, className))
                .map(HibernateDDLGenerator::loadExistingClass)
                .filter(clazz -> clazz.getAnnotation(Entity.class) != null)
                .collect(toList());
    }

    private static Class<?> loadExistingClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractClassNameFromFileName(URL dir, String filePath) {
        String dirPath = dir.getPath().substring(1);
        String filePlattform = filePath.replaceAll("\\\\", "/");
        String className = filePlattform.substring(dirPath.length());
        className = className.substring(0, className.indexOf(".class"));
        className = className.replaceAll("/", ".");
        return className;
    }

    private static SchemaExport buildSchemaExport(DataSource dataSource, File intermediateFile, List<Class<?>> allEntityClasses) throws NamingException {
        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
            .applySetting(Environment.DIALECT, H2Dialect.class.getCanonicalName())
            .applySetting(Environment.CONNECTION_PROVIDER , DatasourceConnectionProviderImpl.class.getCanonicalName())
            .applySetting(Environment.DATASOURCE, dataSource)
            .build();

        MetadataSources sources = new MetadataSources(serviceRegistry);
        allEntityClasses.forEach(sources::addAnnotatedClass);

        SchemaExport schemaExport = new SchemaExport(new MetadataBuilderImpl(sources).build());
        schemaExport.setDelimiter(";");
        schemaExport.setFormat(true);
        schemaExport.setOutputFile(intermediateFile == null ? null : intermediateFile.getAbsolutePath());
        return schemaExport;
    }

    public static void dropEntityTables(DataSource dataSource, URL dir) throws Exception {
        handleEntityTables(dataSource, dir, true);
    }

    public static void createEntityTables(DataSource dataSource, URL dir) throws Exception {
        handleEntityTables(dataSource, dir, false);
    }

    private static void handleEntityTables(DataSource dataSource, URL dir, boolean drop) throws Exception {
        List<Class<?>> allEntityClasses = findEntityClasses(dir);
        SchemaExport schemaExport = buildSchemaExport(dataSource, null, allEntityClasses);
        PrintStream stdout = System.out;
        OutputStream stringOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stringOut));
        if(drop) {
            schemaExport.execute(true, true, true, false);
        } else {
            schemaExport.execute(true, true, false, true);
        }
        System.setOut(stdout);
        Logger.getLogger(HibernateDDLGenerator.class.getSimpleName()).info(stringOut.toString());
    }
}