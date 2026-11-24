package ru.mcs.spi.project;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Неизменяемый поисковый запрос. Attribute filters не привязаны к конкретному языковому формату. */
public record SearchQuery(String text, SearchScope scope, String subtree,
                          Map<String, String> attributeFilters, int limit) {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_TEXT_LENGTH = 1_000;
    private static final String METADATA_DIR = ".manuscriptium";

    public SearchQuery {
        text = text == null ? "" : text.trim();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Search text is too long");
        }
        scope = Objects.requireNonNullElse(scope, SearchScope.ALL);
        subtree = normalizeSubtree(subtree);
        attributeFilters = attributeFilters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributeFilters));
        attributeFilters.forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z0-9_.-]{1,100}")
                    || value == null || value.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("Invalid search attribute filter");
            }
        });
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("Search limit must be between 1 and 1000");
        }
    }

    public boolean isEmpty() {
        return text.isEmpty() && subtree.isEmpty() && attributeFilters.isEmpty();
    }

    private static String normalizeSubtree(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String path = value.trim().replace('\\', '/');
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.startsWith("/") || path.matches("^[A-Za-z]:($|/).*")) {
            throw new IllegalArgumentException("Search subtree must be relative");
        }
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Search subtree contains an invalid path segment");
            }
        }
        if (parts[0].equals(METADATA_DIR) || parts[0].equals(".git")) {
            throw new IllegalArgumentException("Search subtree is outside the document hierarchy");
        }
        return path;
    }
}
