package ru.mcs.spi.editor.style;

import java.util.List;
import ru.mcs.spi.editor.BlockKind;

/** Вычисляет стиль узла по snapshot темы без обращения к UI или файловой системе. */
public interface StyleResolver {

    ComputedStyle resolve(BlockKind kind, List<String> classes, ElementState state, StyleSheet sheet);
}
