package ru.mcs.spi.action;

/** Группа действий: основа меню, подменю и тулбаров. */
public abstract class ActionGroup extends AnAction {

    private boolean popup;

    protected ActionGroup() {
        super("");
        this.popup = false;
    }

    protected ActionGroup(String text, boolean popup) {
        super(text);
        this.popup = popup;
    }

    /** true — группа отображается подменю; false — её элементы вставляются «по месту». */
    public boolean isPopup() {
        return popup;
    }

    public void setPopup(boolean popup) {
        this.popup = popup;
    }

    /** Структурный запрос: {@code e} может быть null. Динамические группы должны это учитывать. */
    public abstract AnAction[] getChildren(AnActionEvent e);

    @Override
    public final void actionPerformed(AnActionEvent e) {
        // группы сами по себе ничего не выполняют
    }
}
