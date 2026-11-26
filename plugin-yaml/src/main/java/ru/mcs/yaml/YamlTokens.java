package ru.mcs.yaml;

import java.util.List;

/** Идентификаторы токенов подсветки YAML. */
public final class YamlTokens {

    public static final String KEY = "yaml.key";
    public static final String STRING = "yaml.string";
    public static final String NUMBER = "yaml.number";
    public static final String CONSTANT = "yaml.constant";
    public static final String COMMENT = "yaml.comment";
    public static final String ANCHOR = "yaml.anchor";
    public static final String TAG = "yaml.tag";
    public static final String MARKER = "yaml.marker";
    public static final String PUNCT = "yaml.punct";

    public static final List<String> ALL = List.of(KEY, STRING, NUMBER, CONSTANT, COMMENT, ANCHOR, TAG, MARKER, PUNCT);

    private YamlTokens() {
    }
}
