package hfe.tools;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.internal.MetadataBuilderImpl;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;

import javax.naming.NamingException;
import javax.persistence.Entity;
import javax.sql.DataSource;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

public class HibernateDDLGenerator {

    public static void dropEntityTables(DataSource dataSource, String dialect, URL dir) throws Exception {
        handleEntityTables(dataSource, dialect, dir, true);
    }

    public static void createEntityTables(DataSource dataSource, String dialect, URL dir) throws Exception {
        handleEntityTables(dataSource, dialect, dir, false);
    }

    // #########################################

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

    private static SchemaExport buildSchemaExport(File intermediateFile) throws NamingException, SQLException {
        SchemaExport schemaExport = new SchemaExport();
        schemaExport.setFormat(true);
        schemaExport.setOutputFile(intermediateFile == null ? null : intermediateFile.getAbsolutePath());
        return schemaExport;
    }

    private static void handleEntityTables(DataSource dataSource, String dialect, URL dir, boolean drop) throws Exception {

        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, dialect)
                .applySetting(AvailableSettings.CONNECTION_PROVIDER , DatasourceConnectionProviderImpl.class.getCanonicalName())
                .applySetting(AvailableSettings.DATASOURCE, dataSource)
                //.applySetting(AvailableSettings.URL, "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MVCC=TRUE;MV_STORE=TRUE")
                //.applySetting(AvailableSettings.USER, "sa")
                //.applySetting(AvailableSettings.PASS, "sa")
                //.applySetting(AvailableSettings.DRIVER, "org.h2.jdbcx.JdbcDataSource")
                .build();

        List<Class<?>> allEntityClasses = findEntityClasses(dir);
        MetadataSources sources = new MetadataSources(serviceRegistry);
        allEntityClasses.forEach(sources::addAnnotatedClass);
        MetadataImplementor metadataImplementor =new MetadataBuilderImpl(sources).build();

        SchemaExport schemaExport = new SchemaExport();
        PrintStream stdout = System.out;
        OutputStream stringOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stringOut));
        if(drop) {
            schemaExport.execute(EnumSet.of(TargetType.DATABASE, TargetType.STDOUT), SchemaExport.Action.DROP, metadataImplementor, serviceRegistry);
            //schemaExport.execute(true, true, true, false);
        } else {
            schemaExport.execute(EnumSet.of(TargetType.DATABASE, TargetType.STDOUT), SchemaExport.Action.CREATE, metadataImplementor, serviceRegistry);
        }
        System.setOut(stdout);
        Logger.getLogger(HibernateDDLGenerator.class.getSimpleName()).info(stringOut.toString());
    }
}