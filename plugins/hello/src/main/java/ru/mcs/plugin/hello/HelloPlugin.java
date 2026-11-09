package ru.mcs.plugin.hello;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.ui.ToolWindowAnchor;

public final class HelloPlugin implements Plugin {

    @Override
    public void start(PluginContext ctx) {
        ctx.registerToolWindow("hello.panel", "Hello", ToolWindowAnchor.LEFT, () -> {
            Module m = HelloPlugin.class.getModule();
            String layer = m.getLayer() == ModuleLayer.boot() ? "boot layer" : "plugin layer";
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("<html>Plugin: " + ctx.pluginId()
                    + "<br>Module: " + m.getName()
                    + "<br>Loaded in: " + layer + "</html>", SwingConstants.CENTER), BorderLayout.CENTER);
            return panel;
        });
        ctx.registerAction("hello.say", "Say hello",
                () -> JOptionPane.showMessageDialog(null, "Hello from a ModuleLayer plugin!"));
        ctx.log("started");
    }
}
