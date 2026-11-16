package ru.mcs.spi.swing.ui;

import ru.mcs.spi.ui.ToolWindow;
public interface ToolWindowManager {

    /** @throws IllegalArgumentException если ID уже занят */
    ToolWindow register(ToolWindowSpec spec);

    ToolWindow get(String id);

    void unregister(String id);
}
