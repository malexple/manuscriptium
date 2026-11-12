package ru.mcs.core.editor;

import java.nio.file.Path;
import javax.swing.JComponent;

/** Внутренняя точка навигации; позволяет тестировать редактор без Vault и окон. */
interface ObsidianNavigation {

    void open(Path source, ObsidianSyntax.Link link, JComponent anchor);
}
