package ru.mcs.core.plugin.contrib;

import javax.swing.Icon;
import ru.mcs.core.plugin.PluginActivator;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;

/**
 * Заглушка действия из plugin.yml: показывает текст из манифеста и загружает плагин при первом выполнении.
 * После загрузки делегирует update/actionPerformed настоящему действию.
 */
final class ProxyAction extends AnAction {

    private static final System.Logger LOG = System.getLogger(ProxyAction.class.getName());

    private final String pluginId;
    private final String actionId;
    private final PluginActivator activator;
    private volatile AnAction delegate;

    ProxyAction(String text, String pluginId, String actionId, PluginActivator activator) {
        this(text, pluginId, actionId, activator, null);
    }

    ProxyAction(String text, String pluginId, String actionId, PluginActivator activator, Icon icon) {
        super(text, null, icon);
        this.pluginId = pluginId;
        this.actionId = actionId;
        this.activator = activator;
    }

    @Override
    public void update(AnActionEvent e) {
        AnAction d = delegate;
        if (d != null) {
            d.update(e);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        AnAction d = resolve();
        if (d != null) {
            d.actionPerformed(e);
        }
    }

    private AnAction resolve() {
        AnAction d = delegate;
        if (d != null) {
            return d;
        }
        try {
            d = activator.activate(pluginId).createAction(actionId);
            if (d == null) {
                throw new IllegalStateException("Plugin " + pluginId + " did not create action " + actionId);
            }
            delegate = d;
            return d;
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.ERROR, "Cannot run action " + actionId, ex);
            return null;
        }
    }
}
