package ru.mcs.plugin.hello;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.editor.FileEditorProvider;

/** Демонстрация: плагин поставляет свой редактор для файлов *.hello (приоритет выше встроенного текстового). */
final class HelloEditorProvider implements FileEditorProvider {

    @Override
    public String getId() {
        return "hello.editor";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean accept(Path file) {
        return file.getFileName().toString().endsWith(".hello") && Files.isRegularFile(file);
    }

    @Override
    public FileEditor createEditor(Path file) throws IOException {
        String text = Files.readString(file);
        JLabel label = new JLabel("<html><h2>Hello editor</h2><pre>" + escape(text) + "</pre></html>",
                SwingConstants.CENTER);
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
                return label;
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

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
