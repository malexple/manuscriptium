package ru.mcs.spi.editor.style;

import java.util.Objects;

/** Нефатальная диагностика загрузки или разрешения CSS темы. */
public record CssDiagnostic(String source, int line, Level level, String message) {

    public enum Level {
        INFO, WARNING, ERROR
    }

    public CssDiagnostic {
        source = Objects.requireNonNullElse(source, "<css>");
        if (line < 0) {
            throw new IllegalArgumentException("line must be non-negative");
        }
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(message, "message");
    }

    public static CssDiagnostic warning(String source, int line, String message) {
        return new CssDiagnostic(source, line, Level.WARNING, message);
    }
}
