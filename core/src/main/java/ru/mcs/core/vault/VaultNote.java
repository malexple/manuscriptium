package ru.mcs.core.vault;

import java.nio.file.Path;

/** Лёгкое представление заметки для дерева; полный текст остаётся в Markdown и SQLite-кэше. */
public record VaultNote(Path file, String path, String title) {
}
