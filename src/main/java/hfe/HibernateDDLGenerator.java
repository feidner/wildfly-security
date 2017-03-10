package hfe;

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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

class HibernateDDLGenerator {

    private static List<Class<?>> findEntityClasses(String directoryPath, String preffixPath) {
        return FileUtils.listFiles(new File(directoryPath), new String[]{"class"}, true).stream()
                .map(File::getPath)
                .map(className -> extractClassNameFromFileName(className, preffixPath))
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

    private static String extractClassNameFromFileName(String filePath, String pathName) {
        String className = filePath.substring(filePath.lastIndexOf(pathName + "\\"), filePath.indexOf(".class"));
        className = className.replace(pathName + "\\", "");
        className = className.replaceAll("\\\\", ".");
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

    static void dropAndCreateEntityTables(DataSource dataSource, String dir, String preffixPath) throws Exception {
        List<Class<?>> allEntityClasses = findEntityClasses(dir, preffixPath);
        SchemaExport schemaExport = buildSchemaExport(dataSource, null, allEntityClasses);
        PrintStream stdout = System.out;
        OutputStream stringOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stringOut));
        schemaExport.execute(true, true, true, false);
        for(int i = 0; i++ < 30; stringOut.write('-'));
        schemaExport.execute(true, true, false, true);
        System.setOut(stdout);
        Logger.getLogger(HibernateDDLGenerator.class.getSimpleName()).info(stringOut.toString());
        readAllTablesFromDatabaseExceptRebuildTable(dataSource);
    }

    private static void readAllTablesFromDatabaseExceptRebuildTable(DataSource dataSource) throws NamingException, SQLException {
        Statement statement = dataSource.getConnection().createStatement();
        statement.execute("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME");
        ResultSet rs = statement.getResultSet();
        Logger.getLogger(HibernateDDLGenerator.class.getSimpleName()).info("Tabellen......");
        while(rs.next()) {
            Logger.getLogger(HibernateDDLGenerator.class.getSimpleName()).info(rs.getString(1));
        }
        rs.close();
        statement.close();
    }
}