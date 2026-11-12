package ru.mcs.plugin.template;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;

/** Минимальная точка входа: действие и панель создаются после активации onDemand. */
public final class TemplatePlugin implements Plugin {

    private PluginContext context;

    @Override
    public void start(PluginContext context) {
        this.context = context;
        context.log("Template plugin started");
    }

    @Override
    public AnAction createAction(String id) {
        if (!id.equals("template.hello")) {
            return null;
        }
        return new AnAction(context.i18n().get("action.hello")) {
            @Override
            public void actionPerformed(AnActionEvent event) {
                context.notifications().info(
                        context.i18n().get("notification.title"),
                        context.i18n().get("notification.message", event.getPlace()));
            }
        };
    }

    @Override
    public JComponent createToolWindowContent(String id) {
        if (!id.equals("template.panel")) {
            return null;
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(context.i18n().get("panel.text", context.pluginId()), SwingConstants.CENTER),
                BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void stop() {
        context = null;
    }
}
