package hfe;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.internal.MetadataBuilderImpl;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaExport;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.Entity;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

public class HibernateDDLGenerator {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[^\\w]");


    private void export(StringBuilder sb, List<Class<?>> allEntityClasses, Operation operation) {
        export(sb, operation, allEntityClasses);
    }

    private void transformToTargetFile(String subSystemSqlScriptName, File intermediateFile) {
        try {
            String fileContent = FileUtils.readFileToString(intermediateFile, StandardCharsets.ISO_8859_1);
            intermediateFile.delete();

            checkIdentifierLengths(fileContent);
            String adjustedFileContent = removeWaste(fileContent);

            File targetFile = new File(subSystemSqlScriptName);
            FileUtils.writeStringToFile(targetFile, adjustedFileContent, StandardCharsets.ISO_8859_1);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            intermediateFile.delete();
        }
    }

    private List<Class<?>> findEntityClasses(String directoryPath, String preffixPath) {
        return FileUtils.listFiles(new File(directoryPath), new String[]{"class"}, true).stream()
                .map(File::getPath)
                .map(className -> extractClassNameFromFileName(className, preffixPath))
                .map(this::loadExistingClass)
                .filter(clazz -> clazz.getAnnotation(Entity.class) != null)
                .collect(toList());
    }

    private Class<?> loadExistingClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private String extractClassNameFromFileName(String filePath, String pathName) {
        String className = filePath.substring(filePath.lastIndexOf(pathName + "\\"), filePath.indexOf(".class"));
        className = className.replace(pathName + "\\", "");
        className = className.replaceAll("\\\\", ".");
        return className;
    }

    private String removeWaste(String fileContent) {
        String adjustedFileContent = Pattern.compile("\n    ").matcher(fileContent).replaceAll("\n");
        if (adjustedFileContent.charAt(0) == '\n') {
            adjustedFileContent = adjustedFileContent.substring(1);
        }
        return adjustedFileContent;
    }

    private void checkIdentifierLengths(String fileContent) {
        for (String partString : WHITESPACE_PATTERN.split(fileContent)) {
            if (partString.length() > 30) {
                throw new RuntimeException("Identifier " + partString + " has more than 30 Characters. Oracle DBMS do not support this.");
            }
        }
    }

    private void export(StringBuilder sb, Operation operation, List<Class<?>> allEntityClasses) {
        SchemaExport schemaExport = buildSchemaExport(null, allEntityClasses);
        boolean consolePrint = true;
        boolean exportInDatabase = true;
        //noinspection ConstantConditions
        PrintStream stdout = System.out;
        OutputStream stringOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stringOut));
        schemaExport.execute(consolePrint, exportInDatabase, operation.isDrop(), operation.isCreate());
        System.setOut(stdout);
        sb.append(stringOut.toString());
    }

    private static DataSource getDataSource() {
        try {
            return (DataSource) new InitialContext().lookup("jboss/datasources/ExampleDS");
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    private SchemaExport buildSchemaExport(File intermediateFile, List<Class<?>> allEntityClasses) {
        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
            .applySetting(Environment.DIALECT, "org.hibernate.dialect.H2Dialect")
            .applySetting(Environment.CONNECTION_PROVIDER , DatasourceConnectionProviderImpl.class.getCanonicalName())
            .applySetting(Environment.DATASOURCE, getDataSource())
            .build();

        MetadataSources sources = new MetadataSources(serviceRegistry);
        allEntityClasses.forEach(sources::addAnnotatedClass);

        SchemaExport schemaExport = new SchemaExport(new MetadataBuilderImpl(sources).build());
        schemaExport.setDelimiter(";");
        schemaExport.setFormat(true);
        schemaExport.setOutputFile(intermediateFile == null ? null : intermediateFile.getAbsolutePath());
        return schemaExport;
    }

    public static void print(String dir, String preffixPath) {
        HibernateDDLGenerator generator = new HibernateDDLGenerator();
        List<Class<?>> allEntityClasses = generator.findEntityClasses(dir, preffixPath);
        StringBuilder sb = new StringBuilder();
        generator.export(sb, allEntityClasses, Operation.DROP);
        generator.export(sb, allEntityClasses, Operation.CREATE);

        readAllTablesFromDatabaseExceptRebuildTable();
    }

    private static void readAllTablesFromDatabaseExceptRebuildTable() {
        try {
            Statement statement = getDataSource().getConnection().createStatement();
            statement.execute("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME");
            ResultSet rs = statement.getResultSet();
            Logger.getLogger(HibernateDDLGenerator.class.getSimpleName()).info("Tabellen......");
            while(rs.next()) {
                Logger.getLogger(HibernateDDLGenerator.class.getSimpleName()).info(rs.getString(1));
            }
            rs.close();
            statement.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private enum Operation {
        CREATE("create"),
        DROP("drop");

        private final String scriptPrefix;

        Operation(String scriptPrefix) {
            this.scriptPrefix = scriptPrefix;
        }

        public boolean isCreate() {
            return this == CREATE;
        }

        public boolean isDrop() {
            return !isCreate();
        }
    }
}