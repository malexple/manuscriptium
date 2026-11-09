package ru.mcs.spi.action;

public final class Separator extends AnAction {

    public static final Separator INSTANCE = new Separator();

    private Separator() {
        super("");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
}
