package ru.mcs.core.project;

import java.nio.file.Path;

/** Один результат Lucene. Абсолютный file вычислен из того же Project и относительного ключа, что и SQLite-индекс. */
record ProjectSearchResult(Path file, String path, String title, String snippet, float score) {
}
