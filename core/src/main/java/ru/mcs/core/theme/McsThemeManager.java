package ru.mcs.core.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import ru.mcs.core.settings.AppSettings;

/** Темы приложения: FlatLaf + JSON-токены, системный режим и горячая перезагрузка файлов. */
public final class McsThemeManager implements AutoCloseable {

    static final String SYSTEM = AppSettings.SYSTEM_THEME;
    private static final System.Logger LOG = System.getLogger(McsThemeManager.class.getName());
    private static final List<String> BUILT_INS = List.of("light", "dark");
    private static final long DEFAULT_OS_POLL_MS = 2000;
    private static final Comparator<ThemeDefinition> ORDER = Comparator
            .comparingInt((ThemeDefinition t) -> switch (t.id()) {
                case "light" -> 0;
                case "dark" -> 1;
                default -> 2;
            })
            .thenComparing(t -> t.name().toLowerCase(java.util.Locale.ROOT))
            .thenComparing(ThemeDefinition::id);

    public record ThemeInfo(String id, String name, boolean dark) {
    }

    private final AppSettings settings;
    private final Path directory;
    private final SystemThemeDetector detector;
    private final long osPollMs;
    private final Map<String, Object> tokenBackup = new HashMap<>();

    private volatile Map<String, ThemeDefinition> themes = Map.of();
    private volatile boolean dark;
    private volatile boolean running;
    private WatchService watchService;
    private Thread watcher;

    public McsThemeManager(AppSettings settings, Path directory) {
        this(settings, directory, SystemThemeDetector.platform(), DEFAULT_OS_POLL_MS);
    }

    McsThemeManager(AppSettings settings, Path directory, SystemThemeDetector detector, long osPollMs) {
        this.settings = settings;
        this.directory = directory.toAbsolutePath().normalize();
        this.detector = detector;
        this.osPollMs = osPollMs;
        reloadThemes();
    }

    public Path directory() {
        return directory;
    }

    public String selectedTheme() {
        return settings.theme();
    }

    public boolean isDark() {
        return dark;
    }

    public List<ThemeInfo> getThemes() {
        return themes.values().stream().sorted(ORDER)
                .map(t -> new ThemeInfo(t.id(), t.name(), t.dark()))
                .toList();
    }

    /** Выбор применяется без перезапуска. Сохранением состояния управляет вызвавший SettingsDialog. */
    public void setTheme(String id) {
        if (!SYSTEM.equals(id) && !themes.containsKey(id)) {
            throw new IllegalArgumentException("Unknown theme: " + id);
        }
        settings.setTheme(id);
        applyOnEdt();
    }

    /** Материализует встроенные файлы для редактирования, применяет выбор и запускает наблюдатель. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        materializeBuiltIns();
        reloadThemes();
        applyOnEdt();
        startWatcher();
    }

    /** Перечитывает все JSON-файлы. При ошибке одного файла остальные темы остаются доступны. */
    public final synchronized void reloadThemes() {
        Map<String, ThemeDefinition> loaded = new LinkedHashMap<>();
        for (String id : BUILT_INS) {
            try (Reader reader = resourceReader(id + ".json")) {
                ThemeDefinition theme = ThemeJson.parse(reader, id, null);
                loaded.put(theme.id(), theme);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "Cannot load built-in theme: " + id, e);
            }
        }
        if (Files.isDirectory(directory)) {
            List<Path> files;
            try (var stream = Files.list(directory)) {
                files = stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .toList();
            } catch (IOException e) {
                files = List.of();
                LOG.log(System.Logger.Level.WARNING, "Cannot list theme directory: " + directory, e);
            }
            for (Path file : files) {
                String fallbackId = file.getFileName().toString().replaceFirst("\\.json$", "");
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    ThemeDefinition theme = ThemeJson.parse(reader, fallbackId, file);
                    loaded.put(theme.id(), theme);
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "Cannot load theme file: " + file, e);
                }
            }
        }
        themes = Map.copyOf(loaded);
    }

    private void materializeBuiltIns() {
        try {
            Files.createDirectories(directory);
            for (String id : BUILT_INS) {
                Path target = directory.resolve(id + ".json");
                if (!Files.exists(target)) {
                    try (InputStream input = resourceStream(id + ".json")) {
                        Files.copy(input, target);
                    }
                }
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot materialize built-in themes in " + directory, e);
        }
    }

    private void startWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            watcher = Thread.ofPlatform().daemon(true).name("mcs-theme-watcher").start(this::watchLoop);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Theme file watcher is unavailable: " + directory, e);
        }
    }

    private void watchLoop() {
        long lastDetection = 0;
        Optional<Boolean> previousDark = Optional.empty();
        while (running) {
            try {
                long now = System.nanoTime();
                if (TimeUnit.NANOSECONDS.toMillis(now - lastDetection) >= osPollMs) {
                    Optional<Boolean> detected = detector.detect();
                    if (detected.isPresent() && !detected.equals(previousDark)) {
                        previousDark = detected;
                        if (SYSTEM.equals(selectedTheme())) {
                            applyOnEdt();
                        }
                    }
                    lastDetection = now;
                }
                WatchKey key = watchService.poll(osPollMs, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }
                boolean reload = key.pollEvents().stream().anyMatch(event ->
                        event.kind() == StandardWatchEventKinds.OVERFLOW
                                || String.valueOf(event.context()).endsWith(".json"));
                boolean valid = key.reset();
                if (reload) {
                    reloadThemes();
                    applyOnEdt();
                }
                if (!valid) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running) {
                    LOG.log(System.Logger.Level.WARNING, "Theme watcher iteration failed", e);
                }
            }
        }
    }

    private void applyOnEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            applyCurrentTheme();
        } else {
            SwingUtilities.invokeLater(this::applyCurrentTheme);
        }
    }

    private void applyCurrentTheme() {
        ThemeDefinition theme = currentTheme();
        try {
            boolean sameBase = UIManager.getLookAndFeel() instanceof FlatLaf laf && laf.isDark() == theme.dark();
            if (!sameBase) {
                if (theme.dark()) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
                tokenBackup.clear();
            }
            tokenBackup.forEach((key, value) -> {
                if (value == null) {
                    UIManager.getDefaults().remove(key);
                } else {
                    UIManager.put(key, value);
                }
            });
            for (Map.Entry<String, Object> token : theme.tokens().entrySet()) {
                tokenBackup.putIfAbsent(token.getKey(), UIManager.get(token.getKey()));
                UIManager.put(token.getKey(), token.getValue());
            }
            dark = theme.dark();
            FlatLaf.updateUI();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Cannot apply theme: " + theme.id(), e);
        }
    }

    private ThemeDefinition currentTheme() {
        String selected = selectedTheme();
        if (SYSTEM.equals(selected)) {
            String effective = detector.detect().orElse(false) ? "dark" : "light";
            return themes.getOrDefault(effective, themes.values().iterator().next());
        }
        return themes.getOrDefault(selected, themes.get("light"));
    }

    private static InputStream resourceStream(String name) {
        InputStream input = McsThemeManager.class.getResourceAsStream("/ru/mcs/core/theme/" + name);
        if (input == null) {
            throw new IllegalStateException("Missing theme resource: " + name);
        }
        return input;
    }

    private static Reader resourceReader(String name) {
        return new java.io.InputStreamReader(resourceStream(name), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
        if (watcher != null) {
            watcher.interrupt();
            try {
                watcher.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
