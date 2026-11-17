package ru.mcs.core.project;

import java.nio.file.Path;

/** Лёгкое представление документа для дерева; полный текст остаётся в файле и SQLite-кэше. */
public record ProjectNote(Path file, String path, String title) {
}
