package ru.mcs.core.plugin;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import ru.mcs.core.plugin.contrib.ContributionRegistrar;
import ru.mcs.spi.McsSdk;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;

/**
 * Находит плагины в {@code <pluginsDir>/<plugin>/plugin.yml + *.jar} каждого переданного каталога,
 * объединяет их в один реестр, объявляет вклады и загружает (активирует).
 * Активация: плагин с {@code activation: onStartup} (по умолчанию) загружается в {@link #loadAll()}; плагин с
 * {@code onDemand} — при первом использовании своего вклада (или явно через {@link #activate}).
 * Каждый плагин получает собственный {@link ModuleLayer}; родитель — boot-слой (spi, core) и слои плагинов
 * из depends-on. {@code afterStop} вызывается после остановки, неудачного объявления или неудачного старта
 * плагина — там снимаются все его регистрации (включая заглушки вкладов).
 */
public final class PluginManager {

    private static final System.Logger LOG = System.getLogger(PluginManager.class.getName());
    static final String SDK_INCOMPATIBLE_FAILURE = "incompatible SDK:";

    private static final class Declared {
        final PluginDescriptor descriptor;
        final PluginContext context;
        ModuleLayer layer;
        Plugin plugin;
        Throwable failure;

        Declared(PluginDescriptor descriptor, PluginContext context) {
            this.descriptor = descriptor;
            this.context = context;
        }
    }

    private final List<Path> pluginsDirs;
    private final Function<PluginDescriptor, PluginContext> contextFactory;
    private final Consumer<PluginDescriptor> afterStop;
    private final Predicate<String> enabled;
    private final Map<String, Declared> declared = new LinkedHashMap<>();
    private final Map<String, Declared> started = new LinkedHashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();
    private volatile BiConsumer<String, String> failureListener = (id, reason) -> { };

    public PluginManager(Path pluginsDir, Function<PluginDescriptor, PluginContext> contextFactory) {
        this(pluginsDir, contextFactory, d -> { });
    }

    public PluginManager(Path pluginsDir, Function<PluginDescriptor, PluginContext> contextFactory,
                         Consumer<PluginDescriptor> afterStop) {
        this(pluginsDir, contextFactory, afterStop, id -> true);
    }

    public PluginManager(Path pluginsDir, Function<PluginDescriptor, PluginContext> contextFactory,
                         Consumer<PluginDescriptor> afterStop, Predicate<String> enabled) {
        this(List.of(pluginsDir), contextFactory, afterStop, enabled);
    }

    public PluginManager(List<Path> pluginsDirs, Function<PluginDescriptor, PluginContext> contextFactory,
                         Consumer<PluginDescriptor> afterStop, Predicate<String> enabled) {
        this.pluginsDirs = List.copyOf(pluginsDirs);
        this.contextFactory = contextFactory;
        this.afterStop = afterStop;
        this.enabled = enabled;
    }

    /** Вызывается при любой ошибке плагина (манифест, объявление вкладов, загрузка): (pluginId, причина). */
    public void setFailureListener(BiConsumer<String, String> listener) {
        this.failureListener = listener;
    }

    public synchronized List<PluginDescriptor> discover() throws IOException {
        List<PluginDescriptor> result = new ArrayList<>();
        for (Path pluginsDir : pluginsDirs) {
            discover(pluginsDir, result);
        }
        return result;
    }

    private void discover(Path pluginsDir, List<PluginDescriptor> result) throws IOException {
        if (!Files.isDirectory(pluginsDir)) {
            return;
        }
        List<Path> dirs;
        try (Stream<Path> s = Files.list(pluginsDir)) {
            dirs = s.filter(Files::isDirectory)
                    .filter(dir -> !dir.getFileName().toString().startsWith("."))
                    .sorted().toList();
        }
        for (Path dir : dirs) {
            Path yml = dir.resolve("plugin.yml");
            if (!Files.isRegularFile(yml)) {
                continue;
            }
            try {
                result.add(PluginDescriptorParser.parse(yml));
            } catch (Exception e) {
                String id = dir.getFileName().toString();
                fail(id, "bad plugin.yml: " + e.getMessage());
                LOG.log(System.Logger.Level.WARNING, "Bad plugin.yml in " + dir, e);
                // Сохраняем повреждённый плагин в реестре, чтобы Settings показал ошибку, а не скрыл его.
                result.add(new PluginDescriptor(id, id, "—", "—", "—", List.of(), List.of(), dir));
            }
        }
    }

    /** Объявляет вклады включённых плагинов и загружает те, что с activation=onStartup. */
    public synchronized void loadAll() throws IOException {
        List<PluginDescriptor> enabledPlugins = discover().stream()
                .filter(d -> enabled.test(d.id()))
                .filter(d -> !failures.containsKey(d.id()))
                .filter(this::validateSdk)
                .toList();
        PluginOrdering.OrderResult order = PluginOrdering.sort(enabledPlugins);
        order.errors().forEach(this::fail);
        for (PluginDescriptor d : order.ordered()) {
            declare(d);
        }
        for (PluginDescriptor d : order.ordered()) {
            Declared dc = declared.get(d.id());
            if (dc != null && dc.failure == null && d.activation() == Activation.ON_STARTUP) {
                try {
                    activate(d.id());
                } catch (Exception ignored) {
                    // ошибка уже записана в failures и передана слушателю
                }
            }
        }
    }

    private boolean validateSdk(PluginDescriptor d) {
        try {
            if (SdkCompatibility.isCompatible(d.sdkVersion())) {
                return true;
            }
        } catch (IllegalArgumentException e) {
            fail(d.id(), "invalid sdk-version '" + d.sdkVersion() + "': " + e.getMessage());
            return false;
        }
        fail(d.id(), SDK_INCOMPATIBLE_FAILURE + " required " + d.sdkVersion()
                + ", application " + McsSdk.VERSION);
        return false;
    }

    private void declare(PluginDescriptor d) {
        try {
            PluginContext ctx = contextFactory.apply(d);
            declared.put(d.id(), new Declared(d, ctx));
            ContributionRegistrar.register(d, ctx, this::activate);
        } catch (Throwable t) {
            declared.remove(d.id());
            fail(d.id(), "cannot register contributions: " + t);
            LOG.log(System.Logger.Level.ERROR, "Plugin declaration failed: " + d.id(), t);
            runAfterStop(d);
        }
    }

    /**
     * Загружает плагин вместе с его зависимостями, если он ещё не загружен. Вызывать на EDT: start() обычно
     * создаёт UI. Повторный вход во время старта (плагин сам использует свой вклад) возвращает тот же экземпляр.
     */
    public synchronized Plugin activate(String id) throws Exception {
        Declared dc = declared.get(id);
        if (dc == null) {
            throw new IllegalStateException("Unknown plugin: " + id);
        }
        if (dc.failure != null) {
            throw new IllegalStateException("Plugin failed earlier: " + id + " (" + dc.failure + ")");
        }
        if (dc.plugin != null) {
            return dc.plugin;
        }
        try {
            for (String dep : dc.descriptor.dependsOn()) {
                activate(dep);
            }
            start(dc);
            LOG.log(System.Logger.Level.INFO, "Plugin started: " + id + " " + dc.descriptor.version());
            return dc.plugin;
        } catch (Throwable t) {
            dc.failure = t;
            dc.plugin = null;
            started.remove(id);
            fail(id, t.toString());
            LOG.log(System.Logger.Level.ERROR, "Plugin failed: " + id, t);
            runAfterStop(dc.descriptor);
            if (t instanceof Exception e) {
                throw e;
            }
            throw new IllegalStateException(t);
        }
    }

    /** Загружает все объявленные плагины (smoke-проверка образа проверяет и ленивые). */
    public synchronized void activateAll() {
        for (String id : new ArrayList<>(declared.keySet())) {
            try {
                activate(id);
            } catch (Exception ignored) {
                // записано в failures
            }
        }
    }

    private void start(Declared dc) throws Exception {
        PluginDescriptor d = dc.descriptor;
        if (!SdkCompatibility.isCompatible(d.sdkVersion())) {
            throw new IllegalStateException("Plugin requires SDK " + d.sdkVersion()
                    + ", application provides " + McsSdk.VERSION);
        }
        List<Path> jars;
        try (Stream<Path> s = Files.list(d.directory())) {
            jars = s.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
        }
        if (jars.isEmpty()) {
            throw new IllegalStateException("No .jar files in " + d.directory());
        }
        List<ModuleLayer> parents = new ArrayList<>();
        parents.add(ModuleLayer.boot());
        for (String depId : d.dependsOn()) {
            Declared dep = declared.get(depId);
            if (dep == null || dep.layer == null) {
                throw new IllegalStateException("Dependency not loaded: " + depId);
            }
            parents.add(dep.layer);
        }
        ModuleFinder finder = ModuleFinder.of(jars.toArray(Path[]::new));
        List<Configuration> parentConfigs = parents.stream().map(ModuleLayer::configuration).toList();
        Configuration cf = Configuration.resolve(finder, parentConfigs, ModuleFinder.of(), List.of(d.module()));
        ModuleLayer layer = ModuleLayer
                .defineModulesWithOneLoader(cf, parents, PluginManager.class.getClassLoader())
                .layer();

        Plugin plugin = ServiceLoader.load(layer, Plugin.class).stream()
                .filter(p -> p.type().getModule().getLayer() == layer)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Module " + d.module() + " does not provide ru.mcs.spi.Plugin"))
                .get();
        dc.layer = layer;
        dc.plugin = plugin;
        plugin.start(dc.context);
        started.put(d.id(), dc);
    }

    public synchronized void stopAll() {
        List<Declared> reversed = new ArrayList<>(started.values());
        Collections.reverse(reversed);
        for (Declared dc : reversed) {
            try {
                dc.plugin.stop();
            } catch (Throwable t) {
                LOG.log(System.Logger.Level.WARNING, "Plugin stop failed: " + dc.descriptor.id(), t);
            }
            runAfterStop(dc.descriptor);
        }
        // Объявленные, но так и не загруженные плагины: снять заглушки вкладов.
        for (Declared dc : declared.values()) {
            if (dc.failure == null && !started.containsKey(dc.descriptor.id())) {
                runAfterStop(dc.descriptor);
            }
        }
        started.clear();
        declared.clear();
    }

    private void fail(String id, String reason) {
        if (reason.equals(failures.put(id, reason))) {
            return;
        }
        try {
            failureListener.accept(id, reason);
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.WARNING, "Failure listener failed", t);
        }
    }

    private void runAfterStop(PluginDescriptor d) {
        try {
            afterStop.accept(d);
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.WARNING, "afterStop failed: " + d.id(), t);
        }
    }

    /** Плагины, код которых загружен и запущен. */
    public synchronized List<String> loadedIds() {
        return List.copyOf(started.keySet());
    }

    /** Все плагины с успешно объявленными вкладами (включая ещё не загруженные). */
    public synchronized List<String> declaredIds() {
        return List.copyOf(declared.keySet());
    }

    public synchronized Map<String, String> failures() {
        return Map.copyOf(failures);
    }
}
