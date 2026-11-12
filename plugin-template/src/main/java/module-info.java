module ru.mcs.plugin.template {
    requires ru.mcs.spi;

    // Разрешает хосту читать SVG-ресурсы named-модуля.
    opens ru.mcs.plugin.template.icons;

    provides ru.mcs.spi.Plugin with ru.mcs.plugin.template.TemplatePlugin;
}
