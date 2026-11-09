package ru.mcs.spi.ui;

import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * Описание панели. {@code content} вызывается лениво — при первом показе, на EDT.
 * {@code showByDefault} действует только пока пользователь не менял видимость (иначе побеждает сохранённая раскладка).
 */
public record ToolWindowSpec(
        String id,
        String title,
        ToolWindowAnchor anchor,
        Icon icon,
        boolean showByDefault,
        Supplier<JComponent> content) {

    public ToolWindowSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(content, "content");
    }

    public static ToolWindowSpec of(String id, String title, ToolWindowAnchor anchor,
                                    Supplier<JComponent> content) {
        return new ToolWindowSpec(id, title, anchor, null, false, content);
    }

    public ToolWindowSpec withIcon(Icon newIcon) {
        return new ToolWindowSpec(id, title, anchor, newIcon, showByDefault, content);
    }

    public ToolWindowSpec withShowByDefault(boolean value) {
        return new ToolWindowSpec(id, title, anchor, icon, value, content);
    }
}
