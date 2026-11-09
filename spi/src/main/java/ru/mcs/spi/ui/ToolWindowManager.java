package ru.mcs.spi.ui;

public interface ToolWindowManager {

    /** @throws IllegalArgumentException если ID уже занят */
    ToolWindow register(ToolWindowSpec spec);

    ToolWindow get(String id);

    void unregister(String id);
}
