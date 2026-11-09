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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import ru.mcs.spi.McsSdk;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;

/**
 * Загружает плагины из {@code <pluginsDir>/<plugin>/plugin.yml + *.jar}.
 * Каждый плагин получает собственный {@link ModuleLayer}; родитель — boot-слой (spi, core)
 * и слои плагинов из depends-on. {@code afterStop} вызывается после остановки (или неудачного старта)
 * плагина — там снимаются его регистрации.
 */
public final class PluginManager {

    private static final System.Logger LOG = System.getLogger(PluginManager.class.getName());

    private record Loaded(PluginDescriptor descriptor, ModuleLayer layer, Plugin plugin) {
    }

    private final Path pluginsDir;
    private final Function<PluginDescriptor, PluginContext> contextFactory;
    private final Consumer<PluginDescriptor> afterStop;
    private final Map<String, Loaded> loaded = new LinkedHashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();

    public PluginManager(Path pluginsDir, Function<PluginDescriptor, PluginContext> contextFactory) {
        this(pluginsDir, contextFactory, d -> { });
    }

    public PluginManager(Path pluginsDir, Function<PluginDescriptor, PluginContext> contextFactory,
                         Consumer<PluginDescriptor> afterStop) {
        this.pluginsDir = pluginsDir;
        this.contextFactory = contextFactory;
        this.afterStop = afterStop;
    }

    public List<PluginDescriptor> discover() throws IOException {
        if (!Files.isDirectory(pluginsDir)) {
            return List.of();
        }
        List<PluginDescriptor> result = new ArrayList<>();
        List<Path> dirs;
        try (Stream<Path> s = Files.list(pluginsDir)) {
            dirs = s.filter(Files::isDirectory).sorted().toList();
        }
        for (Path dir : dirs) {
            Path yml = dir.resolve("plugin.yml");
            if (!Files.isRegularFile(yml)) {
                continue;
            }
            try {
                result.add(PluginDescriptorParser.parse(yml));
            } catch (Exception e) {
                failures.put(dir.getFileName().toString(), "bad plugin.yml: " + e.getMessage());
                LOG.log(System.Logger.Level.WARNING, "Bad plugin.yml in " + dir, e);
            }
        }
        return result;
    }

    public void loadAll() throws IOException {
        PluginOrdering.OrderResult order = PluginOrdering.sort(discover());
        failures.putAll(order.errors());
        for (PluginDescriptor d : order.ordered()) {
            try {
                loaded.put(d.id(), load(d));
                LOG.log(System.Logger.Level.INFO, "Plugin started: " + d.id() + " " + d.version());
            } catch (Throwable t) {
                failures.put(d.id(), t.toString());
                LOG.log(System.Logger.Level.ERROR, "Plugin failed: " + d.id(), t);
                runAfterStop(d);
            }
        }
    }

    private Loaded load(PluginDescriptor d) throws Exception {
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
        for (String dep : d.dependsOn()) {
            Loaded depLoaded = loaded.get(dep);
            if (depLoaded == null) {
                throw new IllegalStateException("Dependency not loaded: " + dep);
            }
            parents.add(depLoaded.layer());
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
        plugin.start(contextFactory.apply(d));
        return new Loaded(d, layer, plugin);
    }

    public void stopAll() {
        List<Loaded> reversed = new ArrayList<>(loaded.values());
        Collections.reverse(reversed);
        for (Loaded l : reversed) {
            try {
                l.plugin().stop();
            } catch (Throwable t) {
                LOG.log(System.Logger.Level.WARNING, "Plugin stop failed: " + l.descriptor().id(), t);
            }
            runAfterStop(l.descriptor());
        }
        loaded.clear();
    }

    private void runAfterStop(PluginDescriptor d) {
        try {
            afterStop.accept(d);
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.WARNING, "afterStop failed: " + d.id(), t);
        }
    }

    public List<String> loadedIds() {
        return List.copyOf(loaded.keySet());
    }

    public Map<String, String> failures() {
        return Map.copyOf(failures);
    }
}
