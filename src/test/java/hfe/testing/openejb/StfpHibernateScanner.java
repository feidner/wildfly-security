package hfe.testing.openejb;

import hfe.tools.Reject;
import org.apache.commons.io.FileUtils;
import org.hibernate.boot.archive.internal.FileInputStreamAccess;
import org.hibernate.boot.archive.internal.JarFileBasedArchiveDescriptor;
import org.hibernate.boot.archive.internal.StandardArchiveDescriptorFactory;
import org.hibernate.boot.archive.scan.internal.ScanResultCollector;
import org.hibernate.boot.archive.scan.spi.*;
import org.hibernate.boot.archive.spi.ArchiveEntry;
import org.hibernate.boot.archive.spi.InputStreamAccess;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Der Scanne findet alle Entities in Packete, die irgendwo im Namen entity haben.
 * Andere Entities werden nicht gefunden.
 */
public class StfpHibernateScanner implements Scanner {

    @Override
    public ScanResult scan(ScanEnvironment environment, ScanOptions options, ScanParameters params) {
        //ScanResult rs = AbstractScannerImpl.scan(persistenceUnit, scanOptions);
        try {
            URL rootUrl = environment.getRootUrl();
            ScanResultCollector collector = new StfpScanResultCollector(environment, options, params);
            if(rootUrl.getPath().endsWith("jar")) {
                JarFileBasedArchiveDescriptor jar = new JarFileBasedArchiveDescriptor(StandardArchiveDescriptorFactory.INSTANCE, rootUrl, "");
                jar.visitArchive(new AbstractScannerImpl.ArchiveContextImpl( true, collector));
            } else {
                Collection<File> files = FileUtils.listFiles(new File(rootUrl.toURI()), new String[]{"class"}, true);
                Set<File> entities = files.stream().filter(file -> file.getPath().matches(".*entity.*")).collect(Collectors.toSet());
                ClassFileArchiveEntryHandler handler = new ClassFileArchiveEntryHandler(collector);
                entities.forEach(file -> handler.handleEntry(new StfpArchiveEntry(file), null));
            }
            return collector.toScanResult();
        } catch (URISyntaxException e) {
            throw Reject.developmentError(e);
        }
    }

    private static class StfpScanResultCollector extends ScanResultCollector {

        public StfpScanResultCollector(ScanEnvironment environment, ScanOptions options, ScanParameters parameters) {
            super(environment, options, parameters);
        }

        @Override
        protected boolean isListedOrDetectable(String name, boolean rootUrl) {
            return true;
        }
    }

    private static class StfpArchiveEntry implements ArchiveEntry {
        private File file;

        public StfpArchiveEntry(File file) {
            this.file = file;
        }
        @Override
        public String getName() {
            return file.getAbsolutePath();
        }

        @Override
        public String getNameWithinArchive() {
            return null;
        }

        @Override
        public InputStreamAccess getStreamAccess() {
            return new FileInputStreamAccess( getName(), file );
        }
    }
}