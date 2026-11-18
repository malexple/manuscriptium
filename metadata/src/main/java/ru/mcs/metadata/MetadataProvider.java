package ru.mcs.metadata;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/** Поставляет описание одного модуля; реализация остаётся в модуле-владельце команд. */
public interface MetadataProvider {

    String id();

    default String resource() {
        return "/META-INF/manuscriptium/" + id() + ".json";
    }

    default InputStream open() {
        InputStream stream = getClass().getResourceAsStream(resource());
        if (stream == null) {
            throw new UncheckedIOException("Metadata resource not found: " + resource(),
                    new FileNotFoundException(resource()));
        }
        return stream;
    }

    default String i18nBaseName() {
        return null;
    }

    default ResourceBundle messages(Locale locale) {
        String baseName = Objects.requireNonNull(i18nBaseName(), "i18nBaseName");
        return ResourceBundle.getBundle(baseName, locale, getClass().getModule());
    }
}
