module ru.mcs.plugin.mdeditor {
    requires ru.mcs.spi;

    opens ru.mcs.plugin.mdeditor.icons;

    provides ru.mcs.spi.Plugin with ru.mcs.plugin.mdeditor.MarkdownEditorPlugin;
}
