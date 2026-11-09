package ru.mcs.core.plugin.contrib;

import javax.swing.JComponent;
import ru.mcs.core.plugin.Contributions;
import ru.mcs.core.plugin.PluginActivator;
import ru.mcs.core.plugin.PluginDescriptor;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.action.DefaultActionGroup;
import ru.mcs.spi.ui.ToolWindowSpec;

/**
 * Регистрирует вклады из plugin.yml через контекст плагина, не загружая его код:
 * действия и группы попадают в меню/тулбар, панели — на stripes, провайдеры редакторов — в реестр.
 * Всё регистрируется на имя плагина, поэтому снимается обычной очисткой при его выгрузке.
 * Тексты вида {@code "%ключ"} переводятся через {@code ctx.i18n()}; {@code "%%текст"} — литерал, начинающийся с «%».
 */
public final class ContributionRegistrar {

    private ContributionRegistrar() {
    }

    public static void register(PluginDescriptor descriptor, PluginContext ctx, PluginActivator activator) {
        Contributions c = descriptor.contributions();
        if (c.isEmpty()) {
            return;
        }
        String pluginId = descriptor.id();
        ActionManager actions = ctx.actions();

        // Сначала создаём все группы, потом связываем: группы могут ссылаться друг на друга в любом порядке.
        for (Contributions.ActionGroupDecl g : c.actionGroups()) {
            actions.registerAction(g.id(), new DefaultActionGroup(text(ctx, g.text()), g.popup()));
        }
        for (Contributions.ActionGroupDecl g : c.actionGroups()) {
            addTo(actions, g.groups(), g.id());
        }
        for (Contributions.ActionDecl a : c.actions()) {
            actions.registerAction(a.id(), new ProxyAction(text(ctx, a.text()), pluginId, a.id(), activator));
            addTo(actions, a.groups(), a.id());
        }
        for (Contributions.ToolWindowDecl t : c.toolWindows()) {
            ToolWindowSpec spec = ToolWindowSpec
                    .of(t.id(), text(ctx, t.title()), t.anchor(),
                            () -> toolWindowContent(activator, pluginId, t.id()))
                    .withShowByDefault(t.showByDefault());
            ctx.toolWindows().register(spec);
        }
        for (Contributions.EditorDecl e : c.fileEditors()) {
            ctx.editors().registerProvider(
                    new ProxyFileEditorProvider(pluginId, e.id(), e.priority(), e.patterns(), activator));
        }
    }

    static String text(PluginContext ctx, String raw) {
        if (raw.startsWith("%%")) {
            return raw.substring(1);
        }
        if (raw.startsWith("%") && raw.length() > 1) {
            return ctx.i18n().get(raw.substring(1));
        }
        return raw;
    }

    private static void addTo(ActionManager actions, Iterable<String> parents, String actionId) {
        for (String parent : parents) {
            actions.addToGroup(parent, actionId, Constraints.LAST);
        }
    }

    private static JComponent toolWindowContent(PluginActivator activator, String pluginId, String id) {
        try {
            JComponent content = activator.activate(pluginId).createToolWindowContent(id);
            if (content == null) {
                throw new IllegalStateException("Plugin " + pluginId + " did not create content for " + id);
            }
            return content;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot activate plugin " + pluginId, e);
        }
    }
}
