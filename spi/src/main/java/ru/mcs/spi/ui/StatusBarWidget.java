package ru.mcs.spi.ui;

import javax.swing.JComponent;

/** Виджет статус-бара. Компонент создаётся один раз (EDT) и обновляется самим виджетом. */
public interface StatusBarWidget {

    enum Position { LEFT, RIGHT }

    String getId();

    Position getPosition();

    JComponent createComponent();

    default void dispose() {
    }
}
