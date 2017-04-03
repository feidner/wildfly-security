package hfe.tools;

import hfe.beans.InitialApplication;
import org.apache.commons.dbutils.handlers.MapListHandler;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HfeUtils {

    private static final Map<String, String> PUBLIC_SCHEMA_NAMES;
    private static final Map<String, Set<Function<String, String>>> DROPP_ALL_STATEMENTS_TO_DATABASES;

    //StreamSupport.stream(Spliterators.spliteratorUnknownSize(dropDatabases.iterator(), 0), false).forEach(drop -> drop.drop(connection));

    static {
        PUBLIC_SCHEMA_NAMES = new HashMap<>();
        PUBLIC_SCHEMA_NAMES.put("H2", "PUBLIC");
        PUBLIC_SCHEMA_NAMES.put("HSQL", "PUBLIC");
        PUBLIC_SCHEMA_NAMES.put("Derby", "APP");
        DROPP_ALL_STATEMENTS_TO_DATABASES = new HashMap<>();
        DROPP_ALL_STATEMENTS_TO_DATABASES.put("H2", Stream.of((Function<String, String>) tablename -> "DROP ALL OBJECTS").collect(Collectors.toSet()));
        DROPP_ALL_STATEMENTS_TO_DATABASES.put("HSQL", Stream.of((Function<String, String>) tablename -> "DROP SCHEMA PUBLIC CASCADE").collect(Collectors.toSet()));
        DROPP_ALL_STATEMENTS_TO_DATABASES.put("Derby", Stream.of((Function<String, String>) tablename -> String.format("drop table %s", tablename)).collect(Collectors.toSet()));
    }

    private HfeUtils() {

    }

    public static URL getClassesFolderURL() throws IOException {
        List<URL> resources = Collections.list(InitialApplication.class.getClassLoader().getResources("."));
        URL classesURL = resources.stream().filter(url -> url.getPath().contains("classes") && !url.getPath().contains("test")).findAny().orElseThrow(() -> new RuntimeException("Verzeichnis classes kann nicht gefunden werden"));
        return classesURL;
    }

    public static List<String> getTableNames(Connection connection) throws SQLException {
        //Query query = entityManager.createNativeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME");
        //tableNames = ((List<String>) query.getResultList());
        DatabaseMetaData metaData = connection.getMetaData();
        String productName = metaData.getDatabaseProductName();
        String schemaPattern = PUBLIC_SCHEMA_NAMES.keySet()
                .stream()
                .filter(productName::contains)
                .map(PUBLIC_SCHEMA_NAMES::get)
                .findAny()
                .orElseThrow(() -> new RuntimeException("SchemaPattern existiert nicht"));
        ResultSet rs = metaData.getTables(null, schemaPattern, null, null);
        List<String> tableNames = new MapListHandler().handle(rs).stream().map(stringObjectMap -> (String)stringObjectMap.get("TABLE_NAME")).collect(Collectors.toList());
        rs.close();
        return tableNames;
    }

    public static void dropAllTablesIfStillSomeExists(Connection connection) throws SQLException {
        List<String> tableNames = HfeUtils.getTableNames(connection);
        if(!tableNames.isEmpty()) {
            try {
                DatabaseMetaData metaData = connection.getMetaData();
                String productName = metaData.getDatabaseProductName();
                Optional<Set<Function<String, String>>> droppers = DROPP_ALL_STATEMENTS_TO_DATABASES.keySet()
                        .stream()
                        .filter(productName::contains)
                        .map(DROPP_ALL_STATEMENTS_TO_DATABASES::get)
                        .findAny();
                if (droppers.isPresent()) {
                    Set<String> sqls = new HashSet<>();
                    Set<Function<String, String>> dropFunctions = droppers.get();
                    tableNames.forEach(tableName -> dropFunctions.forEach(drop -> sqls.add(drop.apply(tableName))));
                    Statement st = connection.createStatement();
                    for (String sql : sqls) {
                        st.execute(sql);
                    }
                    st.close();
                } else {
                    new RuntimeException(String.format("Kein DROP-Statement zur DB %s gefunden!", productName));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Class<?> getTopLevelEnclosingClass(Class<?> clazz) {
        while (clazz.getEnclosingClass() != null) {
            clazz = clazz.getEnclosingClass();
        }
        return clazz;
    }
}
