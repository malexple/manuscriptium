package ru.mcs.spi.project;

import java.nio.file.Path;

/** Один результат полнотекстового поиска. Абсолютный file вычислен из корня Project и относительного пути. */
public record SearchHit(Path file, String path, String title, String snippet, float score) {
}
