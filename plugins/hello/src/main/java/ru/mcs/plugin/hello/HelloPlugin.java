package ru.mcs.plugin.hello;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.action.DefaultActionGroup;
import ru.mcs.spi.editor.EditorDataKeys;
import ru.mcs.spi.editor.EditorTopics;
import ru.mcs.spi.ui.StatusBarTask;
import ru.mcs.spi.ui.ToolWindowAnchor;
import ru.mcs.spi.ui.ToolWindowSpec;

public final class HelloPlugin implements Plugin {

    @Override
    public void start(PluginContext ctx) {
        ctx.toolWindows().register(ToolWindowSpec.of("hello.panel", "Hello", ToolWindowAnchor.LEFT, () -> {
            Module m = HelloPlugin.class.getModule();
            String layer = m.getLayer() == ModuleLayer.boot() ? "boot layer" : "plugin layer";
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("<html>Plugin: " + ctx.pluginId()
                    + "<br>Module: " + m.getName()
                    + "<br>Loaded in: " + layer + "</html>", SwingConstants.CENTER), BorderLayout.CENTER);
            return panel;
        }).withShowByDefault(true));

        ctx.toolWindows().register(ToolWindowSpec.of("hello.log", "Hello Log", ToolWindowAnchor.BOTTOM,
                () -> new JLabel("  Bottom tool window from the plugin", SwingConstants.LEFT)));

        ctx.toolWindows().register(ToolWindowSpec.of("hello.info", "Hello Info", ToolWindowAnchor.RIGHT,
                () -> new JLabel("Right tool window", SwingConstants.CENTER)));

        ctx.editors().registerProvider(new HelloEditorProvider());

        ActiveFileWidget widget = new ActiveFileWidget();
        ctx.statusBar().addWidget(widget);
        ctx.messageBus().subscribeOnEdt(EditorTopics.EDITOR, widget);

        ActionManager am = ctx.actions();

        am.registerAction("hello.say", new AnAction("Say hello") {
            @Override
            public void actionPerformed(AnActionEvent e) {
                String file = String.valueOf(e.getData(EditorDataKeys.ACTIVE_FILE));
                JOptionPane.showMessageDialog(null, "Hello from a ModuleLayer plugin!\nplace: " + e.getPlace()
                        + "\nactive file: " + file);
            }
        });

        am.registerAction("hello.counter", new AnAction("Clicked 0 times") {
            private int clicks;

            @Override
            public void actionPerformed(AnActionEvent e) {
                clicks++;
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setText("Clicked " + clicks + " times");
            }
        });

        am.registerAction("hello.notify", new AnAction("Show notifications") {
            @Override
            public void actionPerformed(AnActionEvent e) {
                ctx.notifications().info("Hello", "Informational message");
                ctx.notifications().warn("Hello", "Warning message");
                ctx.notifications().error("Hello", "Error message (click to dismiss)");
            }
        });

        am.registerAction("hello.task", new AnAction("Run background task (3 s)") {
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
        });

        am.registerAction("hello.group", new DefaultActionGroup("Hello", true));
        am.addToGroup("hello.group", "hello.say", Constraints.LAST);
        am.addToGroup("hello.group", "hello.counter", Constraints.LAST);
        am.addToGroup("hello.group", "hello.notify", Constraints.LAST);
        am.addToGroup("hello.group", "hello.task", Constraints.LAST);
        am.addToGroup(ActionIds.TOOLS_MENU, "hello.group", Constraints.LAST);
        am.addToGroup(ActionIds.MAIN_TOOLBAR, "hello.say", Constraints.LAST);
        am.addToGroup(ActionIds.EDITOR_TAB_POPUP, "hello.say", Constraints.LAST);

        ctx.log("started");
    }
}
