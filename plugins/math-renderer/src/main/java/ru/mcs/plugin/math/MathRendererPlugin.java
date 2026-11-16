package ru.mcs.plugin.math;

import ru.mcs.spi.swing.Plugin;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.editor.MathRenderingService;

/** Регистрирует JDK-only сервис; JLaTeXMath не попадает в SPI или markdown-editor. */
public final class MathRendererPlugin implements Plugin {

    @Override
    public void start(PluginContext context) {
        context.services().register(MathRenderingService.class, new JLaTeXMathRenderingService());
    }
}
