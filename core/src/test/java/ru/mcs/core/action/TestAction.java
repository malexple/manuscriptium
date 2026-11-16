package ru.mcs.core.action;

import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;

class TestAction extends AnAction {

    TestAction(String text) {
        super(text);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
}
