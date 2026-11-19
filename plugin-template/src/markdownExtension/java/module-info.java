module ru.mcs.plugin.markdown.template {
    // Публичный API формата; ru.mcs.spi.swing доступен транзитивно.
    requires ru.mcs.markdown;

    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.markdown.template.MarkdownTemplatePlugin;
}
