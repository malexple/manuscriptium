package ru.mcs.spi.swing.action;

public final class Separator extends AnAction {

    public static final Separator INSTANCE = new Separator();

    /** Metadata может регистрировать несколько независимо адресуемых separators. */
    public Separator() {
        super("");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
}
