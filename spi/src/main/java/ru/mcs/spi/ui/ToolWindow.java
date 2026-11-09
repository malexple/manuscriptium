package ru.mcs.spi.ui;

/** Боковая или нижняя панель. Методы вызываются на EDT. */
public interface ToolWindow {

    String getId();

    String getTitle();

    void setTitle(String title);

    ToolWindowAnchor getAnchor();

    boolean isVisible();

    /** Показать панель; другая панель на той же стороне скрывается. */
    void show();

    void hide();

    default void toggle() {
        if (isVisible()) {
            hide();
        } else {
            show();
        }
    }
}
