package ru.mcs.plugin.hello;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.editor.EditorDataKeys;
import ru.mcs.spi.editor.EditorTopics;
import ru.mcs.spi.editor.FileEditorProvider;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.settings.Configurable;
import ru.mcs.spi.ui.StatusBarTask;

/**
 * Действия, панели и провайдер редакторов объявлены в plugin.yml; здесь они создаются по запросу хоста
 * (после первого использования). В start() остаётся то, чего в манифесте нет: состояние, сервис, виджет.
 * Все тексты — через {@code ctx.i18n()} (messages*.properties в jar плагина).
 */
public final class HelloPlugin implements Plugin {

    private final HelloState state = new HelloState();
    private PluginContext ctx;

    @Override
    public void start(PluginContext context) {
        this.ctx = context;
        ctx.state().register("counter", Scope.APPLICATION, state);
        ctx.services().register(HelloGreeter.class, new HelloGreeter(ctx.i18n()));

        ActiveFileWidget widget = new ActiveFileWidget(ctx.editors().getSelectedFile().orElse(null),
                ctx.i18n().get("widget.nofile"));
        ctx.statusBar().addWidget(widget);
        ctx.messageBus().subscribeOnEdt(EditorTopics.EDITOR, widget);

        ctx.notifications().info(ctx.i18n().get("activated.title"), ctx.i18n().get("activated.message"));
        ctx.log("started");
    }

    @Override
    public AnAction createAction(String id) {
        return switch (id) {
            case "hello.say" -> sayAction();
            case "hello.counter" -> counterAction();
            case "hello.notify" -> notifyAction();
            case "hello.task" -> taskAction();
            default -> null;
        };
    }

    @Override
    public JComponent createToolWindowContent(String id) {
        return switch (id) {
            case "hello.panel" -> infoPanel();
            case "hello.log" -> new JLabel(ctx.i18n().get("log.text"), SwingConstants.LEFT);
            case "hello.info" -> new JLabel(ctx.i18n().get("info.text"), SwingConstants.CENTER);
            default -> null;
        };
    }

    @Override
    public FileEditorProvider createFileEditorProvider(String id) {
        return "hello.editor".equals(id) ? new HelloEditorProvider() : null;
    }

    @Override
    public Configurable createConfigurable(String id) {
        return "hello.settings".equals(id) ? new HelloConfigurable(state, ctx.i18n()) : null;
    }

    private JComponent infoPanel() {
        Module m = HelloPlugin.class.getModule();
        String layer = ctx.i18n().get(m.getLayer() == ModuleLayer.boot() ? "panel.layer.boot" : "panel.layer.plugin");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(ctx.i18n().get("panel.text", ctx.pluginId(), m.getName(), layer),
                SwingConstants.CENTER), BorderLayout.CENTER);
        return panel;
    }

    private AnAction sayAction() {
        return new AnAction(ctx.i18n().get("action.say")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                String file = ctx.i18n().get("say.file", String.valueOf(e.getData(EditorDataKeys.ACTIVE_FILE)));
                String greeting = ctx.services().get(HelloGreeter.class)
                        .greet(ctx.i18n().get("say.place", e.getPlace()));
                JOptionPane.showMessageDialog(null, greeting + "\n" + file);
            }
        };
    }

    private AnAction counterAction() {
        return new AnAction(ctx.i18n().get("action.counter")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                state.increment();
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setText(ctx.i18n().get("counter.text", state.clicks()));
            }
        };
    }

    private AnAction notifyAction() {
        return new AnAction(ctx.i18n().get("action.notify")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                String title = ctx.i18n().get("activated.title");
                ctx.notifications().info(title, ctx.i18n().get("notify.info"));
                ctx.notifications().warn(title, ctx.i18n().get("notify.warn"));
                ctx.notifications().error(title, ctx.i18n().get("notify.error"));
            }
        };
    }

    private AnAction taskAction() {
        return new AnAction(ctx.i18n().get("action.task")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                StatusBarTask task = ctx.statusBar().startTask(ctx.i18n().get("task.title"));
                ctx.statusBar().setMessage(ctx.i18n().get("task.started"));
                Timer timer = new Timer(3000, ev -> {
                    task.finish();
                    ctx.notifications().info(ctx.i18n().get("task.title"), ctx.i18n().get("task.finished"));
                });
                timer.setRepeats(false);
                timer.start();
            }
        };
    }
}
