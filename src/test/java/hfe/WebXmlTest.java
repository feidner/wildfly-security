package hfe;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WebXmlTest {


    @Test
    public void urlPattern_secured_exists() throws IOException, SAXException, JDOMException {
        Set<String> paths = FileUtils.listFilesAndDirs(new File("src/main/webapp"), FalseFileFilter.INSTANCE, TrueFileFilter.INSTANCE).
                stream().map(f -> f.getPath().substring(1 + FilenameUtils.indexOfLastSeparator(f.getPath()))).collect(Collectors.toSet());

        Document document = new SAXBuilder().build("src/main/webapp/WEB-INF/web.xml");
        Set<String> flattened = xmlValues(document.getRootElement(), "url-pattern");
        flattened.forEach(value -> Assert.assertEquals(1, paths.stream().filter(path -> value.contains(path)).count(), paths + ":" + value));
    }

    private static Set<String> xmlValues(Element root, String pattern) {
        return flattened(root).filter(ele -> ele.getName().equals(pattern)).map(ele -> ele.getText()).collect(Collectors.toSet());
    }

    private static Stream<Element> flattened(Element ele) {
        return Stream.concat(Stream.of(ele), ((List<Element>)ele.getChildren()).stream().flatMap(WebXmlTest::flattened));
    }
}
