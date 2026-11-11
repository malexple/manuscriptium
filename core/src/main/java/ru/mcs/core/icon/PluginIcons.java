package ru.mcs.core.icon;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import ru.mcs.core.plugin.PluginDescriptor;

/** Читает декларативные иконки из JAR плагина, не создавая ModuleLayer и не активируя его код. */
public final class PluginIcons {

    private PluginIcons() {
    }

    public static FlatSVGIcon load(PluginDescriptor descriptor, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Icon path must not be blank");
        }
        String resource = path.startsWith("/") ? path.substring(1) : path;
        if (resource.isBlank() || resource.startsWith("../") || resource.contains("/../")) {
            throw new IllegalArgumentException("Invalid icon resource path: " + path);
        }
        try {
            for (Path jar : jars(descriptor.directory())) {
                try (JarFile file = new JarFile(jar.toFile())) {
                    JarEntry entry = file.getJarEntry(resource);
                    if (entry != null && !entry.isDirectory()) {
                        try (InputStream input = file.getInputStream(entry)) {
                            return Icons.load(input, Icons.DEFAULT_SIZE, Icons.DEFAULT_SIZE,
                                    descriptor.id() + ":" + path);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read icon " + path + " from plugin " + descriptor.id(), e);
        }
        throw new IllegalArgumentException("Icon resource not found: " + path + " in plugin " + descriptor.id());
    }

    private static List<Path> jars(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .toList();
        }
    }
}
