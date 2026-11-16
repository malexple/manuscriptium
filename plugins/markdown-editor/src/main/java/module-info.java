module ru.mcs.plugin.mdeditor {
    requires ru.mcs.spi.swing;

    opens ru.mcs.plugin.mdeditor.icons;

    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.mdeditor.MarkdownEditorPlugin;
}
