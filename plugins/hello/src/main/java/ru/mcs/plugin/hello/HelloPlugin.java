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
import ru.mcs.spi.ui.StatusBarTask;

/**
 * Действия, панели и провайдер редакторов объявлены в plugin.yml; здесь они создаются по запросу хоста
 * (после первого использования). В start() остаётся то, чего в манифесте нет: состояние, сервис, виджет.
 */
public final class HelloPlugin implements Plugin {

    private final HelloState state = new HelloState();
    private PluginContext ctx;

    @Override
    public void start(PluginContext context) {
        this.ctx = context;
        ctx.state().register("counter", Scope.APPLICATION, state);
        ctx.services().register(HelloGreeter.class, new HelloGreeter());

        ActiveFileWidget widget = new ActiveFileWidget(ctx.editors().getSelectedFile().orElse(null));
        ctx.statusBar().addWidget(widget);
        ctx.messageBus().subscribeOnEdt(EditorTopics.EDITOR, widget);

        ctx.notifications().info("Hello", "Plugin activated (lazy)");
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
            case "hello.log" -> new JLabel("  Bottom tool window from the plugin", SwingConstants.LEFT);
            case "hello.info" -> new JLabel("Right tool window", SwingConstants.CENTER);
            default -> null;
        };
    }

    @Override
    public FileEditorProvider createFileEditorProvider(String id) {
        return "hello.editor".equals(id) ? new HelloEditorProvider() : null;
    }

    private JComponent infoPanel() {
        Module m = HelloPlugin.class.getModule();
        String layer = m.getLayer() == ModuleLayer.boot() ? "boot layer" : "plugin layer";
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("<html>Plugin: " + ctx.pluginId()
                + "<br>Module: " + m.getName()
                + "<br>Loaded in: " + layer + "</html>", SwingConstants.CENTER), BorderLayout.CENTER);
        return panel;
    }

    private AnAction sayAction() {
        return new AnAction("Say hello") {
            @Override
            public void actionPerformed(AnActionEvent e) {
                String file = String.valueOf(e.getData(EditorDataKeys.ACTIVE_FILE));
                String greeting = ctx.services().get(HelloGreeter.class).greet("place: " + e.getPlace());
                JOptionPane.showMessageDialog(null, greeting + "\nactive file: " + file);
            }
        };
    }

    private AnAction counterAction() {
        return new AnAction("Counter") {
            @Override
            public void actionPerformed(AnActionEvent e) {
                state.increment();
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setText("Clicked " + state.clicks() + " times (saved)");
            }
        };
    }

    private AnAction notifyAction() {
        return new AnAction("Show notifications") {
            @Override
            public void actionPerformed(AnActionEvent e) {
                ctx.notifications().info("Hello", "Informational message");
                ctx.notifications().warn("Hello", "Warning message");
                ctx.notifications().error("Hello", "Error message (click to dismiss)");
            }
        };
    }

    private AnAction taskAction() {
        return new AnAction("Run background task (3 s)") {
            @Override
            public void actionPerformed(AnActionEvent e) {
                StatusBarTask task = ctx.statusBar().startTask("Hello task");
                ctx.statusBar().setMessage("Hello task started");
                Timer timer = new Timer(3000, ev -> {
                    task.finish();
                    ctx.notifications().info("Hello task", "Finished");
                });
                timer.setRepeats(false);
                timer.start();
            }
        };
    }
}
