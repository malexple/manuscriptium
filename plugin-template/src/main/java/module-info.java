module ru.mcs.plugin.template {
    requires ru.mcs.spi.swing;

    // Разрешает хосту читать SVG-ресурсы named-модуля.
    opens ru.mcs.plugin.template.icons;

    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.template.TemplatePlugin;
}
