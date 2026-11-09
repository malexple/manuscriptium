package ru.mcs.core.i18n;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import ru.mcs.core.plugin.PluginDescriptor;
import ru.mcs.spi.i18n.I18n;

/** Строки плагина: читаются напрямую из его jar-файлов (без загрузки кода и без требований к module-info). */
public final class PluginMessages implements I18n {

    private static final System.Logger LOG = System.getLogger(PluginMessages.class.getName());

    private final Locale locale;
    private final MessageBundle bundle;

    PluginMessages(Locale locale, MessageBundle bundle) {
        this.locale = locale;
        this.bundle = bundle;
    }

    public static PluginMessages create(PluginDescriptor descriptor, Locale locale) {
        if (descriptor.i18n() == null || descriptor.directory() == null) {
            return new PluginMessages(locale, MessageBundle.empty());
        }
        List<Path> jars = jarsOf(descriptor.directory());
        return fromJars(jars, descriptor.i18n(), locale);
    }

    public static PluginMessages fromJars(List<Path> jars, String base, Locale locale) {
        MessageBundle bundle = MessageBundle.load(base, locale, path -> openFromJars(jars, path));
        return new PluginMessages(locale, bundle);
    }

    private static List<Path> jarsOf(Path directory) {
        try (Stream<Path> s = Files.list(directory)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot list plugin directory: " + directory, e);
            return List.of();
        }
    }

    private static InputStream openFromJars(List<Path> jars, String entryName) throws IOException {
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        return new ByteArrayInputStream(in.readAllBytes());
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    @Override
    public String get(String key, Object... args) {
        String pattern = bundle.get(key);
        if (pattern == null) {
            return key;
        }
        return MessageBundle.format(pattern, locale, args);
    }
}
