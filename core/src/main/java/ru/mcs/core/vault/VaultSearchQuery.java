package ru.mcs.core.vault;

import java.util.Objects;

/** Неизменяемый поисковый запрос. Поддерево всегда хранится как переносимый относительный путь с '/'. */
record VaultSearchQuery(String text, VaultSearchScope scope, String subtree, Boolean draft, int limit) {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_TEXT_LENGTH = 1_000;

    VaultSearchQuery {
        text = text == null ? "" : text.trim();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Search text is too long");
        }
        scope = Objects.requireNonNullElse(scope, VaultSearchScope.ALL);
        subtree = normalizeSubtree(subtree);
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("Search limit must be between 1 and 1000");
        }
    }

    boolean isEmpty() {
        return text.isEmpty() && subtree.isEmpty() && draft == null;
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
        if (parts[0].equals(VaultPaths.METADATA_DIR) || parts[0].equals(".git")) {
            throw new IllegalArgumentException("Search subtree is outside the note hierarchy");
        }
        return path;
    }
}
