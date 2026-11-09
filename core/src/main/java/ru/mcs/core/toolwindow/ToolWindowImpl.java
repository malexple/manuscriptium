package ru.mcs.core.toolwindow;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import ru.mcs.spi.ui.ToolWindow;
import ru.mcs.spi.ui.ToolWindowAnchor;
import ru.mcs.spi.ui.ToolWindowSpec;

public final class ToolWindowImpl implements ToolWindow {

    private static final System.Logger LOG = System.getLogger(ToolWindowImpl.class.getName());

    private final ToolWindowManagerImpl manager;
    private final String owner;
    private final ToolWindowSpec spec;
    private volatile String title;
    private JComponent content;

    ToolWindowImpl(ToolWindowManagerImpl manager, String owner, ToolWindowSpec spec) {
        this.manager = manager;
        this.owner = owner;
        this.spec = spec;
        this.title = spec.title();
    }

    String owner() {
        return owner;
    }

    public Icon getIcon() {
        return spec.icon();
    }

    @Override
    public String getId() {
        return spec.id();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
        manager.fireChanged();
    }

    @Override
    public ToolWindowAnchor getAnchor() {
        return spec.anchor();
    }

    @Override
    public boolean isVisible() {
        return manager.isVisible(this);
    }

    @Override
    public void show() {
        manager.show(this);
    }

    @Override
    public void hide() {
        manager.hide(this);
    }

    /** Лениво создаёт содержимое (EDT). Ошибка плагина не должна ронять оболочку. */
    public JComponent content() {
        if (content == null) {
            try {
                content = spec.content().get();
            } catch (Throwable t) {
                LOG.log(System.Logger.Level.ERROR, "Tool window content failed: " + spec.id(), t);
                content = new JLabel("Failed to create '" + spec.title() + "': " + t);
            }
            if (content == null) {
                content = new JLabel("Empty tool window: " + spec.title());
            }
        }
        return content;
    }
}
