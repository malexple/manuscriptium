module ru.mcs.plugin.math {
    requires ru.mcs.spi.swing;
    requires jlatexmath;

    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.math.MathRendererPlugin;
}
