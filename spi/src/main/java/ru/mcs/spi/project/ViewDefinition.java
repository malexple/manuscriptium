package ru.mcs.spi.project;

import java.nio.file.Path;
import java.util.List;

/** Один {@code .manuscriptium/views/<id>.yml}. Списки записей изменяются только через ViewOperations. */
public record ViewDefinition(String id, String name, Path file, List<ViewEntry> entries) {
}
