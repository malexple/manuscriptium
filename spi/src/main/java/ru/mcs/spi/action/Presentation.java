package ru.mcs.spi.action;

import javax.swing.Icon;

/** Отображаемое состояние действия (текст, иконка, доступность, видимость). Не потокобезопасен: работаем на EDT. */
public final class Presentation {

    private String text;
    private String description;
    private Icon icon;
    private boolean enabled = true;
    private boolean visible = true;

    public Presentation() {
        this("");
    }

    public Presentation(String text) {
        this.text = text == null ? "" : text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setEnabledAndVisible(boolean value) {
        this.enabled = value;
        this.visible = value;
    }

    public Presentation copy() {
        Presentation p = new Presentation(text);
        p.description = description;
        p.icon = icon;
        p.enabled = enabled;
        p.visible = visible;
        return p;
    }
}
