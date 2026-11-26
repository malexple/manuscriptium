package ru.mcs.yaml;

import java.util.function.Supplier;
import ru.mcs.editor.view.TokenColors;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.service.AtomicFileStore;
import ru.mcs.spi.swing.editor.keymap.KeymapService;

/** Всё, что нужно редактору от модуля: хранилище, строки, настройки, цвета и сочетания (последние два — по запросу). */
record YamlEditorServices(AtomicFileStore files, I18n i18n, YamlSettings settings, YamlCodeStyleState codeStyle,
                          Supplier<TokenColors> colors, Supplier<KeymapService> keymap) {
}
