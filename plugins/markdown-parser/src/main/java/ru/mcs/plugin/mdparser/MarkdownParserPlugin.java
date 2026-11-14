package ru.mcs.plugin.mdparser;

import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.editor.MarkdownParser;

/** Публикует Flexmark через JDK-only SPI; типы Flexmark не покидают plugin layer. */
public final class MarkdownParserPlugin implements Plugin {

    @Override
    public void start(PluginContext context) {
        context.services().register(MarkdownParser.class, new FlexmarkMarkdownParser());
        context.log("Flexmark Markdown parser registered");
    }
}
