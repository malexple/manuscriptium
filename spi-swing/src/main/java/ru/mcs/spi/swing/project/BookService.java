package ru.mcs.spi.swing.project;

import java.nio.file.Path;
import java.util.List;
import ru.mcs.spi.project.ViewDefinition;
import ru.mcs.spi.swing.editor.FileEditor;

/** Фабрика сквозной книжной проекции view; конкретный текстовый формат принадлежит языковому модулю. */
public interface BookService {

    FileEditor createEditor(Path key, String name, Path root, ViewDefinition view, List<Integer> nodePath);
}
