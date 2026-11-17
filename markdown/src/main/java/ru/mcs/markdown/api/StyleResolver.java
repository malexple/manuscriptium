package ru.mcs.markdown.api;

import ru.mcs.spi.swing.editor.style.ComputedStyle;
import ru.mcs.spi.editor.style.ElementState;
import java.util.List;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.StyleSheet;

/** Вычисляет стиль узла по snapshot темы без обращения к UI или файловой системе. */
public interface StyleResolver {

    ComputedStyle resolve(BlockKind kind, List<String> classes, ElementState state, StyleSheet sheet);
}
