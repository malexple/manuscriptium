package ru.mcs.core.plugin.contrib;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.editor.FileEditorProvider;
import ru.mcs.spi.settings.Configurable;

final class TestPlugin implements Plugin {

    final JLabel content = new JLabel("content");
    final List<Path> editedFiles = new ArrayList<>();
    final Configurable configurable = new Configurable() {
        @Override
        public JComponent createComponent() {
            return new JLabel("settings");
        }

        @Override
        public boolean isModified() {
            return false;
        }

        @Override
        public void apply() {
        }
    };

    private final FileEditorProvider provider = new FileEditorProvider() {
        @Override
        public String getId() {
            return "test.provider";
        }

        @Override
        public boolean accept(Path file) {
            return true;
        }

        @Override
        public FileEditor createEditor(Path file) throws IOException {
            editedFiles.add(file);
            JPanel panel = new JPanel();
            return new FileEditor() {
                @Override
                public Path getFile() {
                    return file;
                }

                @Override
                public String getName() {
                    return file.getFileName().toString();
                }

                @Override
                public JComponent getComponent() {
                    return panel;
                }

                @Override
                public boolean isModified() {
                    return false;
                }

                @Override
                public void save() {
                }

                @Override
                public void addModifiedListener(Runnable listener) {
                }
            };
        }
    };

    @Override
    public void start(PluginContext context) {
    }

    @Override
    public JComponent createToolWindowContent(String id) {
        return content;
    }

    @Override
    public FileEditorProvider createFileEditorProvider(String id) {
        return provider;
    }

    @Override
    public Configurable createConfigurable(String id) {
        return configurable;
    }
}
