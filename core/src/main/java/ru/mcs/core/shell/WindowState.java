package ru.mcs.core.shell;

import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.Timer;
import ru.mcs.spi.service.PersistentStateComponent;

/** Положение и размер окна (id core.window). Если сохранённое положение вне экранов — окно центрируется. */
public final class WindowState implements PersistentStateComponent {

    private static final int MIN_WIDTH = 200;
    private static final int MIN_HEIGHT = 150;
    private static final int DEFAULT_WIDTH = 1200;
    private static final int DEFAULT_HEIGHT = 800;

    private int x;
    private int y;
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private boolean hasBounds;
    private boolean maximized;

    @Override
    public synchronized Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        if (hasBounds) {
            state.put("x", x);
            state.put("y", y);
            state.put("width", width);
            state.put("height", height);
        }
        state.put("maximized", maximized);
        return state;
    }

    @Override
    public synchronized void loadState(Map<String, Object> state) {
        Integer nx = intOf(state.get("x"));
        Integer ny = intOf(state.get("y"));
        Integer nw = intOf(state.get("width"));
        Integer nh = intOf(state.get("height"));
        if (nx != null && ny != null && nw != null && nh != null && nw >= MIN_WIDTH && nh >= MIN_HEIGHT) {
            x = nx;
            y = ny;
            width = nw;
            height = nh;
            hasBounds = true;
        }
        maximized = Boolean.TRUE.equals(state.get("maximized"));
    }

    /** При maximized обычные границы не перезаписываются — после «восстановить» окно вернётся к ним. */
    public synchronized void update(Rectangle bounds, boolean isMaximized) {
        if (!isMaximized && bounds.width >= MIN_WIDTH && bounds.height >= MIN_HEIGHT) {
            x = bounds.x;
            y = bounds.y;
            width = bounds.width;
            height = bounds.height;
            hasBounds = true;
        }
        this.maximized = isMaximized;
    }

    public synchronized Rectangle bounds() {
        return hasBounds ? new Rectangle(x, y, width, height) : null;
    }

    public synchronized boolean isMaximized() {
        return maximized;
    }

    public void applyTo(JFrame frame) {
        Rectangle saved = bounds();
        if (saved != null && intersectsEnough(saved, screenBounds())) {
            frame.setBounds(saved);
        } else {
            frame.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            frame.setLocationRelativeTo(null);
        }
        if (isMaximized()) {
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }

    /** Следит за окном; запись состояния откладывается на 300 мс, чтобы не ловить промежуточные размеры. */
    public void track(JFrame frame) {
        Timer debounce = new Timer(300, e -> capture(frame));
        debounce.setRepeats(false);
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                debounce.restart();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                debounce.restart();
            }
        });
        frame.addWindowStateListener(e -> debounce.restart());
    }

    private void capture(JFrame frame) {
        boolean max = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        update(frame.getBounds(), max);
    }

    static boolean intersectsEnough(Rectangle bounds, List<Rectangle> screens) {
        for (Rectangle screen : screens) {
            Rectangle common = screen.intersection(bounds);
            if (common.width >= 100 && common.height >= 50) {
                return true;
            }
        }
        return false;
    }

    private static List<Rectangle> screenBounds() {
        List<Rectangle> result = new ArrayList<>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            result.add(device.getDefaultConfiguration().getBounds());
        }
        return result;
    }

    private static Integer intOf(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }
}
