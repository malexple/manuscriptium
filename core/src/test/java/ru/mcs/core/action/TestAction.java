package ru.mcs.core.action;

import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;

class TestAction extends AnAction {

    TestAction(String text) {
        super(text);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
}
