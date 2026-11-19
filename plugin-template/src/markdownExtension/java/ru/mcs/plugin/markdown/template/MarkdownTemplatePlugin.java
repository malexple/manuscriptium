package ru.mcs.plugin.markdown.template;

import ru.mcs.markdown.api.BlockKind;
import ru.mcs.markdown.api.MarkdownParser;
import ru.mcs.spi.swing.Plugin;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;

/** Пример расширения формата: использует только экспортированный API модуля Markdown. */
public final class MarkdownTemplatePlugin implements Plugin {

    private static final String SAMPLE = "# Manuscriptium\n\n- first\n- second\n";

    private PluginContext context;
    private MarkdownParser parser;

    @Override
    public void start(PluginContext context) {
        this.context = context;
        parser = context.services().get(MarkdownParser.class);
        context.log("Markdown extension started with " + parser.getClass().getName());
    }

    @Override
    public AnAction createAction(String id) {
        if (!id.equals("markdown.template.inspect")) {
            return null;
        }
        return new AnAction(context.i18n().get("action.inspect")) {
            @Override
            public void actionPerformed(AnActionEvent event) {
                MarkdownParser.ParseResult result = parser.parse(SAMPLE, 1);
                long headings = result.spans().stream().filter(span -> span.kind() == BlockKind.HEADING).count();
                context.notifications().info(context.i18n().get("notification.title"),
                        context.i18n().get("notification.message", result.spans().size(), headings));
                context.log("Markdown extension action: spans=" + result.spans().size()
                        + ", headings=" + headings + ", version=" + result.version());
            }
        };
    }

    @Override
    public void stop() {
        parser = null;
        context = null;
    }
}
