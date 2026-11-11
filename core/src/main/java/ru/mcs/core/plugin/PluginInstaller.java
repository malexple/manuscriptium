package ru.mcs.core.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Локальная установка из папки, ZIP-пакета или JAR с plugin.yml в корне. Код плагина здесь не загружается. */
public final class PluginInstaller {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public enum SourceKind { DIRECTORY, ZIP, JAR }

    /** Проверенный источник и метаданные для диалога подтверждения. */
    public record Inspection(Path source, PluginDescriptor descriptor, SourceKind kind, String manifestEntry) {
    }

    private final Path pluginsDir;

    public PluginInstaller(Path pluginsDir) {
        this.pluginsDir = pluginsDir.toAbsolutePath().normalize();
    }

    public Inspection inspect(Path source) throws IOException {
        Path normalized = source.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            Path manifest = normalized.resolve("plugin.yml");
            if (!Files.isRegularFile(manifest)) {
                throw new IllegalArgumentException("Plugin directory does not contain plugin.yml");
            }
            PluginDescriptor descriptor = PluginDescriptorParser.parse(manifest);
            validateId(descriptor.id());
            requireDirectoryJar(normalized);
            return new Inspection(normalized, descriptor, SourceKind.DIRECTORY, "plugin.yml");
        }
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Plugin source does not exist: " + normalized);
        }
        String name = normalized.getFileName().toString().toLowerCase(Locale.ROOT);
        SourceKind kind = name.endsWith(".zip") ? SourceKind.ZIP : name.endsWith(".jar") ? SourceKind.JAR : null;
        if (kind == null) {
            throw new IllegalArgumentException("Choose a plugin directory, .zip package or .jar with plugin.yml");
        }
        return inspectArchive(normalized, kind);
    }

    public boolean isInstalled(String pluginId) throws IOException {
        return findInstalledDirectory(pluginId) != null;
    }

    /**
     * Новый плагин устанавливается сразу. Обновление существующего подготавливается в скрытый pending-каталог:
     * текущие JAR могут быть заблокированы загрузчиком, особенно на Windows, поэтому их замена откладывается
     * до {@link #applyPending()} при следующем запуске.
     */
    public PluginDescriptor install(Inspection confirmed) throws IOException {
        Inspection current = inspect(confirmed.source());
        if (!current.descriptor().id().equals(confirmed.descriptor().id())) {
            throw new IllegalArgumentException("Plugin package changed after confirmation");
        }
        String id = current.descriptor().id();
        Files.createDirectories(pluginsDir);
        Path existing = findInstalledDirectory(id);
        Path staging = pluginsDir.resolve("." + id + ".installing-" + UUID.randomUUID());
        try {
            copyPackage(current, staging);
            PluginDescriptor staged = PluginDescriptorParser.parse(staging.resolve("plugin.yml"));
            validateId(staged.id());
            if (!staged.id().equals(id)) {
                throw new IllegalArgumentException("Plugin id changed while installing");
            }
            Path target = existing != null ? pendingPath(id) : pluginsDir.resolve(id);
            replaceDirectory(staging, target);
            return PluginDescriptorParser.parse(target.resolve("plugin.yml"));
        } finally {
            deleteQuietly(staging);
        }
    }

    /** Применяет подготовленные обновления до того, как PluginManager откроет JAR текущих версий. */
    public void applyPending() throws IOException {
        if (!Files.isDirectory(pluginsDir)) {
            return;
        }
        List<Path> pending;
        try (Stream<Path> paths = Files.list(pluginsDir)) {
            pending = paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().endsWith(".pending"))
                    .sorted().toList();
        }
        for (Path source : pending) {
            PluginDescriptor descriptor = PluginDescriptorParser.parse(source.resolve("plugin.yml"));
            String id = descriptor.id();
            validateId(id);
            if (!source.equals(pendingPath(id))) {
                throw new IOException("Pending plugin id does not match its directory: " + id);
            }
            Path existing = findInstalledDirectory(id);
            Path target = existing != null ? existing : pluginsDir.resolve(id);
            replaceDirectory(source, target);
        }
    }

    private Path pendingPath(String pluginId) {
        return pluginsDir.resolve("." + pluginId + ".pending");
    }

    private static void replaceDirectory(Path source, Path target) throws IOException {
        Path backup = target.getParent().resolve("." + target.getFileName() + ".backup-" + UUID.randomUUID());
        boolean backedUp = false;
        try {
            if (Files.exists(target)) {
                move(target, backup);
                backedUp = true;
            }
            move(source, target);
            deleteQuietly(backup);
        } catch (IOException | RuntimeException e) {
            if (backedUp && !Files.exists(target) && Files.exists(backup)) {
                try {
                    move(backup, target);
                } catch (IOException rollback) {
                    e.addSuppressed(rollback);
                }
            }
            throw e;
        } finally {
            if (Files.exists(target)) {
                deleteQuietly(backup);
            }
        }
    }

    /** Имя каталога не является идентификатором: например, hello/ содержит ru.mcs.plugin.hello. */
    private Path findInstalledDirectory(String pluginId) throws IOException {
        Path direct = pluginsDir.resolve(pluginId);
        if (Files.exists(direct)) {
            return direct;
        }
        if (!Files.isDirectory(pluginsDir)) {
            return null;
        }
        try (Stream<Path> paths = Files.list(pluginsDir)) {
            for (Path directory : paths.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted().toList()) {
                Path manifest = directory.resolve("plugin.yml");
                if (!Files.isRegularFile(manifest)) {
                    continue;
                }
                try {
                    if (PluginDescriptorParser.parse(manifest).id().equals(pluginId)) {
                        return directory;
                    }
                } catch (IOException | RuntimeException ignored) {
                    // Битый каталог не должен мешать установке нового валидного плагина.
                }
            }
        }
        return null;
    }

    private Inspection inspectArchive(Path source, SourceKind kind) throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
            List<? extends ZipEntry> manifests = zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().equals("plugin.yml") || entry.getName().endsWith("/plugin.yml"))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
            if (manifests.size() != 1) {
                throw new IllegalArgumentException("Plugin archive must contain exactly one plugin.yml");
            }
            ZipEntry manifest = manifests.get(0);
            if (kind == SourceKind.JAR && !manifest.getName().equals("plugin.yml")) {
                throw new IllegalArgumentException("Plugin JAR must contain plugin.yml in its root");
            }
            PluginDescriptor descriptor;
            try (Reader reader = new InputStreamReader(zip.getInputStream(manifest), StandardCharsets.UTF_8)) {
                descriptor = PluginDescriptorParser.parse(reader, source);
            }
            validateId(descriptor.id());
            if (kind == SourceKind.ZIP) {
                String prefix = archivePrefix(manifest.getName());
                boolean hasJar = zip.stream().anyMatch(entry -> !entry.isDirectory()
                        && entry.getName().startsWith(prefix) && entry.getName().endsWith(".jar"));
                if (!hasJar) {
                    throw new IllegalArgumentException("Plugin ZIP does not contain a JAR next to plugin.yml");
                }
            }
            return new Inspection(source, descriptor, kind, manifest.getName());
        }
    }

    private void copyPackage(Inspection inspection, Path staging) throws IOException {
        Files.createDirectories(staging);
        switch (inspection.kind()) {
            case DIRECTORY -> copyDirectory(inspection.source(), staging);
            case ZIP -> extractZip(inspection, staging);
            case JAR -> copyJar(inspection, staging);
        }
    }

    private static void copyDirectory(Path source, Path staging) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Symbolic links are not allowed in plugin packages: " + path);
                }
                Path target = safeResolve(staging, source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
    }

    private static void extractZip(Inspection inspection, Path staging) throws IOException {
        String prefix = archivePrefix(inspection.manifestEntry());
        try (ZipFile zip = new ZipFile(inspection.source().toFile(), StandardCharsets.UTF_8)) {
            for (ZipEntry entry : zip.stream().toList()) {
                if (!entry.getName().startsWith(prefix)) {
                    continue;
                }
                String relative = entry.getName().substring(prefix.length());
                if (relative.isBlank()) {
                    continue;
                }
                Path target = safeResolve(staging, relative);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (InputStream input = zip.getInputStream(entry)) {
                        Files.copy(input, target);
                    }
                }
            }
        }
    }

    private static void copyJar(Inspection inspection, Path staging) throws IOException {
        Files.copy(inspection.source(), staging.resolve(inspection.source().getFileName().toString()));
        try (ZipFile zip = new ZipFile(inspection.source().toFile(), StandardCharsets.UTF_8)) {
            ZipEntry manifest = zip.getEntry(inspection.manifestEntry());
            try (InputStream input = zip.getInputStream(manifest)) {
                Files.copy(input, staging.resolve("plugin.yml"));
            }
        }
    }

    private static Path safeResolve(Path staging, String relative) throws IOException {
        String normalizedSeparators = relative.replace('\\', '/');
        if (normalizedSeparators.startsWith("/") || normalizedSeparators.matches("^[A-Za-z]:.*")) {
            throw new IOException("Absolute path in plugin package: " + relative);
        }
        Path target = staging.resolve(normalizedSeparators).normalize();
        if (!target.startsWith(staging) || target.equals(staging) && !relative.isBlank()) {
            throw new IOException("Unsafe path in plugin package: " + relative);
        }
        return target;
    }

    private static String archivePrefix(String manifestEntry) {
        int slash = manifestEntry.lastIndexOf('/');
        return slash < 0 ? "" : manifestEntry.substring(0, slash + 1);
    }

    private static void requireDirectoryJar(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            if (paths.noneMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))) {
                throw new IllegalArgumentException("Plugin directory does not contain a JAR");
            }
        }
    }

    private static void validateId(String id) {
        if (!SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Unsafe plugin id: " + id);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static void deleteQuietly(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        } catch (IOException ignored) {
            // Скрытый staging/backup не участвует в discover; основная операция уже завершена.
        }
    }
}
