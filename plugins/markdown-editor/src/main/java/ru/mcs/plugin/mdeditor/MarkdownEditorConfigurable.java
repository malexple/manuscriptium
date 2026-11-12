package ru.mcs.plugin.mdeditor;

import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.settings.Configurable;

/** Настройка режима, который получают новые Markdown-вкладки. */
final class MarkdownEditorConfigurable implements Configurable {

    private final MarkdownEditorState state;
    private final I18n i18n;
    private final JComboBox<EditorMode> defaultMode = new JComboBox<>(EditorMode.values());

    MarkdownEditorConfigurable(MarkdownEditorState state, I18n i18n) {
        this.state = state;
        this.i18n = i18n;
        defaultMode.setName("markdown.editor.settings.defaultMode");
        defaultMode.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                String text = value == EditorMode.SOURCE ? i18n.get("mode.source") : i18n.get("mode.canvas");
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
        reset();
    }

    @Override
    public JComponent createComponent() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel(i18n.get("settings.defaultMode")));
        panel.add(defaultMode);
        return panel;
    }

    @Override
    public boolean isModified() {
        return defaultMode.getSelectedItem() != state.defaultMode();
    }

    @Override
    public void apply() {
        state.setDefaultMode((EditorMode) defaultMode.getSelectedItem());
    }

    @Override
    public void reset() {
        defaultMode.setSelectedItem(state.defaultMode());
    }
}
