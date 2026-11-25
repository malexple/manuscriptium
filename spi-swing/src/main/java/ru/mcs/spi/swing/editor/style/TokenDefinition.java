package ru.mcs.spi.swing.editor.style;

import java.util.Objects;

/** Токен подсветки: идентификатор, название для пользователя и цвета по умолчанию (RGB) для светлой и тёмной темы. */
public record TokenDefinition(String id, String name, int lightRgb, int darkRgb) {

    public TokenDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        lightRgb &= 0xFFFFFF;
        darkRgb &= 0xFFFFFF;
    }

    public int rgb(boolean dark) {
        return dark ? darkRgb : lightRgb;
    }
}
