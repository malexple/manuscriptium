module ru.mcs.plugin.math {
    requires ru.mcs.spi;
    requires jlatexmath;

    provides ru.mcs.spi.Plugin with ru.mcs.plugin.math.MathRendererPlugin;
}
