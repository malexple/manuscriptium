package ru.mcs.plugin.mdeditor.mode;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import ru.mcs.spi.editor.style.ComputedStyle;

/** Приглушает неактивные блоки; интенсивность может быть переопределена CSS-переменной темы. */
public final class FocusMode {

    public static final String ALPHA_VARIABLE = "--focus-mode-alpha";
    public static final float DEFAULT_ALPHA = 0.35f;

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Создаёт независимый контекст рисования приглушённого блока. Вызывающий код обязан его dispose(). */
    public Graphics2D wrap(Graphics2D graphics, ComputedStyle rootStyle) {
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setComposite(AlphaComposite.SrcOver.derive(alpha(rootStyle)));
        return copy;
    }

    public float alpha(ComputedStyle rootStyle) {
        String value = rootStyle == null ? null : rootStyle.variables().get(ALPHA_VARIABLE);
        if (value != null) {
            try {
                float alpha = Float.parseFloat(value.trim());
                if (Float.isFinite(alpha)) {
                    return Math.max(0.05f, Math.min(1.0f, alpha));
                }
            } catch (NumberFormatException ignored) {
                // Некорректное пользовательское значение не должно ломать рендеринг.
            }
        }
        return DEFAULT_ALPHA;
    }
}
