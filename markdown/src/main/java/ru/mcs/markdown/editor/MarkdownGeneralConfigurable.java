package ru.mcs.markdown.editor;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import ru.mcs.spi.swing.settings.Configurable;

/** Editor → General → Markdown: показ номеров строк на вкладке «Исходник»; открытые вкладки обновляются сразу. */
final class MarkdownGeneralConfigurable implements Configurable {

    private final MarkdownSourceViewState state;
    private final Consumer<Boolean> applied;
    private final JCheckBox lineNumbers;

    MarkdownGeneralConfigurable(MarkdownSourceViewState state, Consumer<Boolean> applied,
                                UnaryOperator<String> text) {
        this.state = state;
        this.applied = applied;
        lineNumbers = new JCheckBox(text.apply("settings.sourceLineNumbers"));
        lineNumbers.setName("markdown.settings.lineNumbers");
        reset();
    }

    @Override
    public JComponent createComponent() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(lineNumbers);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(row, BorderLayout.NORTH);
        return panel;
    }

    @Override
    public boolean isModified() {
        return lineNumbers.isSelected() != state.showLineNumbers();
    }

    @Override
    public void apply() {
        boolean visible = lineNumbers.isSelected();
        state.setShowLineNumbers(visible);
        applied.accept(visible);
    }

    @Override
    public void reset() {
        lineNumbers.setSelected(state.showLineNumbers());
    }
}
