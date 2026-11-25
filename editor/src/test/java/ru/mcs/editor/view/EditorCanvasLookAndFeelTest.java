package ru.mcs.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;

class EditorCanvasLookAndFeelTest {

    @Test
    void defaultColorsFollowThemeChangeButExplicitColorsStay() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Object oldBackground = UIManager.get("EditorPane.background");
            Object oldForeground = UIManager.get("EditorPane.foreground");
            EditorCanvas themed = new EditorCanvas();
            EditorCanvas explicit = new EditorCanvas();
            try {
                explicit.setBackground(Color.WHITE);
                UIManager.put("EditorPane.background", new ColorUIResource(0x1e222a));
                UIManager.put("EditorPane.foreground", new ColorUIResource(0xd8dee9));

                themed.updateUI();
                explicit.updateUI();

                assertThat(themed.getBackground()).isEqualTo(new Color(0x1e222a));
                assertThat(themed.getForeground()).isEqualTo(new Color(0xd8dee9));
                assertThat(explicit.getBackground()).isEqualTo(Color.WHITE);
                assertThat(explicit.getForeground()).isEqualTo(new Color(0xd8dee9));
            } finally {
                UIManager.put("EditorPane.background", oldBackground);
                UIManager.put("EditorPane.foreground", oldForeground);
                themed.dispose();
                explicit.dispose();
            }
        });
    }
}
