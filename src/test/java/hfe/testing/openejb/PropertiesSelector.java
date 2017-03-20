package hfe.testing.openejb;

import hfe.tools.Reject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Logger;


class PropertiesSelector {

    private PropertiesSelector() {

    }

    public static void main(String[] args) {
        Logger.getLogger("MAU").info("run me");
        Reject.ifFalse("Genau ein Parameter wird erwartet, ein Ordner", args.length == 1);
        runWithSelectedProperties(null, file -> {
            try {
                FileUtils.copyFile(file, new File(args[0] + File.separator + "selected.properties"));
            } catch (IOException e) {
                throw Reject.developmentError(e);
            }
        });
    }

    static void runWithSelectedProperties(Consumer<Properties> selectedProperties, Consumer<File> selectedFile) {
        JPanel pane = new JPanel();
        JFrame frame = createFrame("Ziel", pane, new Dimension(200, 400));
        pane.setLayout(new GridLayout(0, 1));
        ButtonGroup group = new ButtonGroup();
        Action runAction = new AbstractAction("RUN") {
            @Override
            public void actionPerformed(ActionEvent event) {
                Enumeration<AbstractButton> buttons = group.getElements();
                while (buttons.hasMoreElements()) {
                    PropertyRadioButton button = (PropertyRadioButton)buttons.nextElement();
                    if (button.isSelected()) {
                        frame.setVisible(false);
                        if(selectedProperties != null) {
                            Properties properties = PropertiesProvider.createFromStandalonXmlCheckedIn(button.file);
                            selectedProperties.accept(properties);
                        }
                        if(selectedFile != null) {
                            selectedFile.accept(button.file);
                        }
                        frame.dispose();
                    }
                }
            }
        };
        pane.add(new JButton(runAction));
        JRadioButton selectThisButton = null;
        for (File propertyFile : getPropertyFiles()) {
            JRadioButton button = new PropertyRadioButton(propertyFile);
            group.add(button);
            if (selectThisButton == null) {
                selectThisButton = button;
                selectThisButton.setSelected(true);
            }
            pane.add(button);
        }
        pane.add(new JButton(runAction));
        frame.setVisible(true);
    }

    static void readH2Name(Consumer<String> h2NameConsumer) {
        JPanel pane = new JPanel();
        JFrame frame = createFrame("Ziel", pane, new Dimension(200, 400));
        pane.setLayout(new GridLayout(0, 1));
        JTextField h2Name = new JTextField();
        Action runAction = new AbstractAction("RUN") {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.setVisible(false);
                h2NameConsumer.accept(h2Name.getText());
                frame.dispose();
            }
        };
        pane.add(h2Name);
        pane.add(new JButton(runAction));
        frame.setVisible(true);
    }

    public static Collection<File> getPropertyFiles() {
        Collection<File> propertyFiles = FileUtils.listFiles(new File("src/test/resources"), new AbstractFileFilter() {
            @Override
            public boolean accept(File file) {
                return  file.getName().endsWith("properties") &&
                        file.getPath().contains("src") &&
                        file.getPath().contains(String.format("db-properties"));
            }
        }, DirectoryFileFilter.INSTANCE);
        return propertyFiles;
    }

    private static class PropertyRadioButton extends JRadioButton {
        private File file;
        PropertyRadioButton(File file) {
            super(getFileName(file).replace("__", ""));
            this.file = file;
        }
        private static String getFileName(File file) {
            return file.getName().substring(0, file.getName().indexOf('.'));
        }
    }

    private static JFrame createFrame(String title, Container pane, Dimension dimension) {
        JFrame frame = new JFrame();
        frame.setContentPane(pane);
        frame.setTitle(title);
        frame.setLocationRelativeTo(null);
        frame.setSize(dimension);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        return frame;
    }
}
