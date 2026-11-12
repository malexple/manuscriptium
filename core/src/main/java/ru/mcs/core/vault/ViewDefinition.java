package ru.mcs.core.vault;

import java.nio.file.Path;
import java.util.List;

/** Один {@code .manuscriptium/views/<id>.yml}. Списки записей изменяются только через ViewOperations. */
record ViewDefinition(String id, String name, Path file, List<ViewEntry> entries) {
}
