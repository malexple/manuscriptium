package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.swing.editor.keymap.Shortcuts;

class ShortcutsTest {

    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    @Test
    void parsesModifiersLettersDigitsAndNamedKeys() {
        assertThat(Shortcuts.parse("shortcut shift Z", CTRL))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_Z, CTRL | InputEvent.SHIFT_DOWN_MASK));
        assertThat(Shortcuts.parse("alt shift 5", CTRL))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_5, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(Shortcuts.parse("shortcut SLASH", CTRL)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, CTRL));
        assertThat(Shortcuts.parse("F8", CTRL)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0));
        assertThat(Shortcuts.parse("shortcut alt BACK_SPACE", CTRL))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, CTRL | InputEvent.ALT_DOWN_MASK));
    }

    @Test
    void shortcutFollowsThePlatformMask() {
        assertThat(Shortcuts.parse("shortcut L", InputEvent.META_DOWN_MASK))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.META_DOWN_MASK));
    }

    @Test
    void formatIsPortableAndRoundTrips() {
        KeyStroke stroke = Shortcuts.parse("shortcut alt L", CTRL);

        assertThat(Shortcuts.format(stroke, CTRL)).isEqualTo("shortcut alt L");
        assertThat(Shortcuts.parse(Shortcuts.format(stroke, CTRL), CTRL)).isEqualTo(stroke);
        assertThat(Shortcuts.format(stroke, InputEvent.META_DOWN_MASK)).isEqualTo("ctrl alt L");
        assertThat(Shortcuts.format(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, CTRL), CTRL))
                .isEqualTo("shortcut CLOSE_BRACKET");
        assertThat(Shortcuts.display(stroke)).endsWith("L");
    }

    @Test
    void blankMeansNoShortcutAndGarbageIsRejected() {
        assertThat(Shortcuts.parse("  ", CTRL)).isNull();
        assertThat(Shortcuts.parse(null, CTRL)).isNull();
        assertThatThrownBy(() -> Shortcuts.parse("shortcut", CTRL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Shortcuts.parse("A B", CTRL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Shortcuts.parse("shortcut NOPE", CTRL)).isInstanceOf(IllegalArgumentException.class);
    }
}
