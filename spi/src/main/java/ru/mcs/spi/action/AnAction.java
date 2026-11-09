package ru.mcs.spi.action;

import javax.swing.Icon;

/**
 * Атомарная операция. ID присваивается при регистрации в {@link ActionManager}.
 * {@link #update} вызывается перед каждым показом и должен быть быстрым (EDT).
 */
public abstract class AnAction {

    private final Presentation template;

    protected AnAction() {
        this.template = new Presentation();
    }

    protected AnAction(String text) {
        this.template = new Presentation(text);
    }

    protected AnAction(String text, String description, Icon icon) {
        this.template = new Presentation(text);
        this.template.setDescription(description);
        this.template.setIcon(icon);
    }

    public final Presentation getTemplatePresentation() {
        return template;
    }

    public abstract void actionPerformed(AnActionEvent e);

    /** Обновить {@code e.getPresentation()} (текст, доступность, видимость). По умолчанию ничего не меняет. */
    public void update(AnActionEvent e) {
    }
}
