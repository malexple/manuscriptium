package ru.mcs.plugin.math;

import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.editor.MathRenderingService;

/** Регистрирует JDK-only сервис; JLaTeXMath не попадает в SPI или markdown-editor. */
public final class MathRendererPlugin implements Plugin {

    @Override
    public void start(PluginContext context) {
        context.services().register(MathRenderingService.class, new JLaTeXMathRenderingService());
    }
}
