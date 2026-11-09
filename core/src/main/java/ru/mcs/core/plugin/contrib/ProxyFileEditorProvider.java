package ru.mcs.core.plugin.contrib;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import ru.mcs.core.plugin.PluginActivator;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.editor.FileEditorProvider;

/** Заглушка провайдера из plugin.yml: подходит по маскам имени файла, плагин загружает при создании редактора. */
final class ProxyFileEditorProvider implements FileEditorProvider {

    private final String pluginId;
    private final String id;
    private final int priority;
    private final List<PathMatcher> matchers;
    private final PluginActivator activator;
    private volatile FileEditorProvider delegate;

    ProxyFileEditorProvider(String pluginId, String id, int priority, List<String> patterns,
                            PluginActivator activator) {
        this.pluginId = pluginId;
        this.id = id;
        this.priority = priority;
        this.activator = activator;
        this.matchers = patterns.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .toList();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean accept(Path file) {
        Path name = file.getFileName();
        return name != null && matchers.stream().anyMatch(m -> m.matches(name));
    }

    @Override
    public FileEditor createEditor(Path file) throws IOException {
        FileEditorProvider d = delegate;
        if (d == null) {
            try {
                d = activator.activate(pluginId).createFileEditorProvider(id);
            } catch (Exception e) {
                throw new IOException("Cannot activate plugin " + pluginId, e);
            }
            if (d == null) {
                throw new IOException("Plugin " + pluginId + " did not create editor provider " + id);
            }
            delegate = d;
        }
        return d.createEditor(file);
    }
}
