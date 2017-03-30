package hfe.testing.openejb;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.shared.utils.PropertyUtils;
import org.apache.openejb.util.PropertyPlaceHolderHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

class PropertiesProvider {

    static final String EMBEDDED_H2 = "embedded.h2";

    private static final String DB_DRIVER_CLASS_PARAMETER = "ds.db.driver.class";
    private static final String STANDALONE_PATH = "/wildfly-config/standalone.xml";

    private static final Function<String, File> FILE_SUPPLIER = name ->
            Stream.of( name, "__" + name).
            map(s -> String.format("/db-properties/%s.properties", s)).
            filter(resourceName -> PropertiesProvider.class.getResource(resourceName) != null).
            map(PropertiesProvider.class::getResource).
            map(PropertiesProvider::toFile).
            findAny().
            orElseThrow(() -> new RuntimeException(String.format("Datei /db-properties/%s.properties kann nicht gefunden werden", name)));

    private PropertiesProvider() {
    }

    static Properties createFromStandalonXmlCheckedIn(String name, String h2DbName) {
        Properties result;
        try {
            result = readDbConnectionProperties(PropertyUtils.loadProperties(FILE_SUPPLIER.apply(name)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Properties properties = result;
        if(StringUtils.isNotEmpty(h2DbName)) {
            // jdbc:h2:file:C:/data/test
            // jdbc:h2:tcp://localhost/~/testH2;DB_CLOSE_DELAY=-1;MVCC=TRUE
            String keyDbUrl = (String) properties.keySet().stream().
                    filter(obj -> obj.toString().contains("JdbcUrl")).
                    findAny().
                    orElseThrow(() -> new RuntimeException("In den Properties kann kein Key mit dem Substring JdbcUrl gefunden werden"));
            //String dbUrl = properties.getProperty(keyDbUrl);
            //String newUrl = dbUrl.substring(0, dbUrl.lastIndexOf("/") + 1) + h2DbName + dbUrl.substring(dbUrl.indexOf(";"));
            h2DbName = (h2DbName.startsWith("/") || h2DbName.contains(":/")) ? h2DbName : "./" + h2DbName;
            properties.put(keyDbUrl, "jdbc:h2:file:" + h2DbName);
            properties.put(EMBEDDED_H2, "TRUE");
        }
        return properties;
    }

    static Properties createFromStandalonXmlCheckedIn(String name) {
        try {
            return readDbConnectionProperties(PropertyUtils.loadProperties(FILE_SUPPLIER.apply(name)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ############## privates ##############################

    private static String evaluateDsName(String jndiName, Predicate<String> filterConstraint) {
        String dsNamePossibility_1 = jndiName.substring(jndiName.lastIndexOf('/') + 1);
        String dsNamePossibility_2 = jndiName.substring(jndiName.lastIndexOf('/') + 1, jndiName.length() - 2); // falls mit DS endet
        return Stream.of(
                dsNamePossibility_1, dsNamePossibility_1.toLowerCase(), dsNamePossibility_1.toUpperCase(),
                dsNamePossibility_2, dsNamePossibility_2.toLowerCase(), dsNamePossibility_2.toUpperCase()).
                filter(filterConstraint).
                findAny().
                orElseThrow(() -> new RuntimeException("jndiName kann im Predicate nicht gefunden werden"));
    }
    private static Properties createJndiPropertiesForDataSourceDefinition(Properties dbFileProperties, Map<String, String> drivers, Element element) {
        Properties properties = new Properties();
        String jndiName = element.getAttribute("jndi-name");
        String dsName = evaluateDsName(jndiName, name -> dbFileProperties.containsKey(name + "." + DB_DRIVER_CLASS_PARAMETER));
        String jndiValue = "new://Resource?type=DataSource";
        if (jndiName.startsWith("java:")) {
            String alias = "/" + jndiName.substring("java:".length());
            jndiValue = String.format("new://Resource?type=DataSource&aliases=%s&JtaManaged=%s", alias, true);
        }
        String driverClass = dbFileProperties.getProperty(dsName + "." + DB_DRIVER_CLASS_PARAMETER);
        if(StringUtils.isEmpty(driverClass)) {
            String dd = getValue(element, "driver", true);
            driverClass = drivers.get(dbFileProperties.getProperty(dd));
        }
        properties.put(jndiName, jndiValue);
        properties.put(jndiName + ".JdbcDriver", driverClass);
        properties.put(jndiName + ".JdbcUrl", dbFileProperties.getProperty(getValue(element, "connection-url", true)));
        properties.put(jndiName + ".Username", dbFileProperties.getProperty(getValue(element, "user-name", true)));
        properties.put(jndiName + ".Password", dbFileProperties.getProperty(getValue(element, "password", true)));
        return properties;
    }

    private static String getValue(Element element, String tag, boolean isParameter) {
        Node node = element.getElementsByTagName(tag).item(0).getChildNodes().item(0);
        return isParameter ? PropertyPlaceHolderHelper.simpleValue(node.getNodeValue()) : node.getNodeValue();
    }

    static Properties readDbConnectionProperties(Properties dbFileProperties) throws ParserConfigurationException, IOException, SAXException {
        Document standaloneXmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().
                parse(PropertiesProvider.class.getResource(STANDALONE_PATH).openStream());
        standaloneXmlDocument.getDocumentElement().normalize();
        NodeList driversElement = standaloneXmlDocument.getElementsByTagName("driver");
        Map<String, String> drivers = new HashMap<>();
        for (int i = 0; i < driversElement.getLength(); i++) {
            Node node = driversElement.item(i);
            if (node.getParentNode().getNodeName().equals("drivers")) {
                Element element = (Element) node;
                String driverName = element.getAttribute("name");
                String driver = getValue(element, "xa-datasource-class", false);
                drivers.put(driverName, driver);
            }
        }
        NodeList datasources = standaloneXmlDocument.getElementsByTagName("datasource");
        Properties all = new Properties();
        for (int i = 0; i < datasources.getLength(); i++) {
            Node node = datasources.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                all.putAll(createJndiPropertiesForDataSourceDefinition(dbFileProperties, drivers, element));
            }
        }
        return all;
    }

    private static File toFile(URL url) {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
