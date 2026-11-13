package ru.mcs.plugin.mdeditor.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Физические свойства файла, которые не отображаются в нормализованном тексте модели. */
public record DocumentProperties(Charset charset, LineEnding lineEnding, boolean bom, boolean mixedLineEndings) {

    public DocumentProperties {
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(lineEnding, "lineEnding");
        if (!StandardCharsets.UTF_8.equals(charset)) {
            throw new IllegalArgumentException("Only UTF-8 Markdown is supported: " + charset);
        }
    }

    public static DocumentProperties utf8(LineEnding lineEnding, boolean bom) {
        return utf8(lineEnding, bom, false);
    }

    public static DocumentProperties utf8(LineEnding lineEnding, boolean bom, boolean mixedLineEndings) {
        return new DocumentProperties(StandardCharsets.UTF_8, lineEnding, bom, mixedLineEndings);
    }
}
