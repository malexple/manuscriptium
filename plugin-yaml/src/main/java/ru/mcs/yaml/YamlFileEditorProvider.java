package ru.mcs.yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.swing.editor.FileEditorProvider;

/** Открывает .yml и .yaml в текстовом редакторе с подсветкой и проверкой. */
final class YamlFileEditorProvider implements FileEditorProvider {

    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final YamlEditorServices services;
    private final List<YamlFileEditor> editors = new CopyOnWriteArrayList<>();

    YamlFileEditorProvider(YamlEditorServices services) {
        this.services = services;
    }

    @Override
    public String getId() {
        return "yaml.editor";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean accept(Path file) {
        if (file == null || file.getFileName() == null || !isYaml(file) || !Files.isRegularFile(file)) {
            return false;
        }
        try {
            return Files.size(file) <= MAX_BYTES;
        } catch (IOException error) {
            return false;
        }
    }

    @Override
    public FileEditor createEditor(Path file) throws IOException {
        if (!accept(file)) {
            throw new IOException("Not a readable YAML file within the size limit: " + file);
        }
        YamlFileEditor editor = new YamlFileEditor(file, services, editors::remove);
        editors.add(editor);
        editor.applySettings();
        editor.useKeymap();
        return editor;
    }

    /** Применяет изменившиеся настройки, цвета и сочетания ко всем открытым редакторам. */
    void refresh() {
        for (YamlFileEditor editor : editors) {
            editor.applySettings();
            editor.useKeymap();
        }
    }

    static boolean isYaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }
}
