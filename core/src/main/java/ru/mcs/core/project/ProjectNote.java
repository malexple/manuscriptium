package ru.mcs.core.project;

import java.nio.file.Path;

/** Лёгкое представление заметки для дерева; полный текст остаётся в Markdown и SQLite-кэше. */
public record ProjectNote(Path file, String path, String title) {
}
