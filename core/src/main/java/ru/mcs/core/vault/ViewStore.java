package ru.mcs.core.vault;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Читает и атомарно сохраняет человекочитаемые {@code .manuscriptium/views/*.yml}. */
final class ViewStore {

    record LoadResult(List<ViewDefinition> views, List<String> errors) {
        LoadResult {
            views = List.copyOf(views);
            errors = List.copyOf(errors);
        }
    }

    private static final Set<String> ROOT_KEYS = Set.of("name", "entries");
    private static final Set<String> ENTRY_KEYS = Set.of(
            "node", "file", "children", "override-title", "visibility", "tags");

    private final Path directory;

    ViewStore(Path root) {
        this.directory = VaultPaths.viewsDir(root);
    }

    LoadResult load() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return new LoadResult(List.of(), List.of());
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.yml")) {
            for (Path file : stream) {
                files.add(file);
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));

        List<ViewDefinition> views = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("not a regular file");
                }
                views.add(read(file));
            } catch (IOException | RuntimeException e) {
                String detail = e.getMessage() == null || e.getMessage().isBlank() ? e.toString() : e.getMessage();
                errors.add(name + ": " + detail);
            }
        }
        return new LoadResult(views, errors);
    }

    private ViewDefinition read(Path file) throws IOException {
        Object raw;
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            raw = new Yaml(new SafeConstructor(options)).load(reader);
        }
        Map<?, ?> map = mapping(raw, "view");
        checkKeys(map, ROOT_KEYS, "view");
        String name = requiredString(map, "name", "view");
        List<ViewEntry> entries = entries(map.get("entries"), "entries");
        String filename = file.getFileName().toString();
        return new ViewDefinition(filename.substring(0, filename.length() - ".yml".length()),
                name, file.toAbsolutePath().normalize(), entries);
    }

    private static List<ViewEntry> entries(Object raw, String path) {
        if (raw == null) {
            return new ArrayList<>();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException(path + " must be a list");
        }
        List<ViewEntry> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(entry(list.get(i), path + "[" + i + "]"));
        }
        return result;
    }

    private static ViewEntry entry(Object raw, String path) {
        Map<?, ?> map = mapping(raw, path);
        checkKeys(map, ENTRY_KEYS, path);
        boolean hasNode = map.get("node") != null;
        boolean hasFile = map.get("file") != null;
        if (hasNode == hasFile) {
            throw new IllegalArgumentException(path + " must contain exactly one of 'node' or 'file'");
        }
        String overrideTitle = optionalString(map, "override-title", path);
        boolean visibility = optionalBoolean(map, "visibility", path);
        List<String> tags = tags(map.get("tags"), path + ".tags");
        if (hasNode) {
            String title = requiredString(map, "node", path);
            return ViewEntry.node(title, overrideTitle, visibility, tags,
                    entries(map.get("children"), path + ".children"));
        }
        if (map.containsKey("children")) {
            throw new IllegalArgumentException(path + ": 'children' is allowed only for a node");
        }
        String file = requiredString(map, "file", path);
        validateRelativeMarkdown(file, path);
        return ViewEntry.file(file, overrideTitle, visibility, tags);
    }

    private static void validateRelativeMarkdown(String file, String path) {
        if (file.indexOf('\\') >= 0 || file.startsWith("/") || file.matches("^[A-Za-z]:($|/).*")) {
            throw new IllegalArgumentException(path + ".file must be a relative path with '/' separators");
        }
        try {
            if (Path.of(file).isAbsolute()) {
                throw new IllegalArgumentException(path + ".file must be relative");
            }
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(path + ".file is invalid: " + e.getMessage(), e);
        }
        String[] parts = file.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException(path + ".file contains an invalid path segment");
            }
        }
        if (parts[0].equals(VaultPaths.METADATA_DIR) || parts[0].equals(".git")) {
            throw new IllegalArgumentException(path + ".file points outside the note hierarchy");
        }
        if (!file.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new IllegalArgumentException(path + ".file must reference a Markdown file");
        }
    }

    void save(ViewDefinition view) throws IOException {
        Files.createDirectories(directory);
        Map<String, Object> rootMap = new LinkedHashMap<>();
        rootMap.put("name", view.name());
        rootMap.put("entries", serializeEntries(view.entries()));
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(2);
        options.setIndentWithIndicator(true);
        options.setSplitLines(false);
        String yaml = new Yaml(options).dump(rootMap);

        Path target = view.file().toAbsolutePath().normalize();
        Path expected = directory.resolve(view.id() + ".yml").toAbsolutePath().normalize();
        if (!target.equals(expected) || !target.startsWith(directory)) {
            throw new IOException("View file is outside its views directory: " + target);
        }
        Path temp = directory.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                var bytes = StandardCharsets.UTF_8.encode(yaml);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static List<Object> serializeEntries(List<ViewEntry> entries) {
        List<Object> result = new ArrayList<>();
        for (ViewEntry entry : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (entry.isNode()) {
                map.put("node", entry.node());
            } else {
                map.put("file", entry.file());
            }
            if (entry.overrideTitle() != null) {
                map.put("override-title", entry.overrideTitle());
            }
            if (!entry.visibility()) {
                map.put("visibility", false);
            }
            if (!entry.tags().isEmpty()) {
                // Клоны могут разделять immutable-список; свежий список не даёт SnakeYAML создать YAML alias.
                map.put("tags", new ArrayList<>(entry.tags()));
            }
            if (entry.isNode()) {
                map.put("children", serializeEntries(entry.children()));
            }
            result.add(map);
        }
        return result;
    }

    private static Map<?, ?> mapping(Object raw, String path) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(path + " must be a YAML mapping");
        }
        return map;
    }

    private static void checkKeys(Map<?, ?> map, Set<String> allowed, String path) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String string) || !allowed.contains(string)) {
                throw new IllegalArgumentException(path + ": unknown key '" + key + "'");
            }
        }
    }

    private static String requiredString(Map<?, ?> map, String key, String path) {
        if (!(map.get(key) instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException(path + "." + key + " must be a non-empty string");
        }
        return value;
    }

    private static String optionalString(Map<?, ?> map, String key, String path) {
        if (!map.containsKey(key)) {
            return null;
        }
        return requiredString(map, key, path);
    }

    private static boolean optionalBoolean(Map<?, ?> map, String key, String path) {
        if (!map.containsKey(key)) {
            return true;
        }
        if (!(map.get(key) instanceof Boolean value)) {
            throw new IllegalArgumentException(path + "." + key + " must be true or false");
        }
        return value;
    }

    private static List<String> tags(Object raw, String path) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException(path + " must be a list");
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof String tag) || tag.isBlank()) {
                throw new IllegalArgumentException(path + "[" + i + "] must be a non-empty string");
            }
            result.add(tag);
        }
        return result;
    }
}
