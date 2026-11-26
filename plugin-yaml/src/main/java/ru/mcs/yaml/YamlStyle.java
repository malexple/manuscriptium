package ru.mcs.yaml;

/** Параметры форматирования YAML. */
record YamlStyle(int indentSize, boolean tabsToSpaces, boolean trimTrailingWhitespace, boolean finalNewline,
                 int maxBlankLines) {

    YamlStyle {
        indentSize = Math.max(1, Math.min(indentSize, 8));
        maxBlankLines = Math.max(0, Math.min(maxBlankLines, 10));
    }

    static YamlStyle defaults() {
        return new YamlStyle(2, true, true, true, 2);
    }
}
