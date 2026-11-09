package ru.mcs.spi.action;

/** Где вызвано действие (action может по-разному вести себя в меню и на тулбаре). */
public final class ActionPlaces {
    public static final String MAIN_MENU = "MainMenu";
    public static final String MAIN_TOOLBAR = "MainToolbar";
    public static final String POPUP = "Popup";

    private ActionPlaces() {
    }
}
