package ru.mcs.core.vault;

import java.nio.file.Path;

/** Один результат Lucene. Абсолютный file вычислен из того же Vault и относительного ключа, что и SQLite-индекс. */
record VaultSearchResult(Path file, String path, String title, String snippet, float score) {
}
