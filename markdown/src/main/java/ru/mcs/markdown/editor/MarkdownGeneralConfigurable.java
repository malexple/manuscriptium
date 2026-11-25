package ru.mcs.markdown.editor;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import ru.mcs.spi.swing.settings.Configurable;

/** Editor → General → Markdown: номера строк и подсветка на вкладке «Исходник»; открытые вкладки обновляются сразу. */
final class MarkdownGeneralConfigurable implements Configurable {

    private final MarkdownSourceViewState state;
    private final Consumer<MarkdownSourceViewState> applied;
    private final JCheckBox lineNumbers;
    private final JCheckBox highlighting;

    MarkdownGeneralConfigurable(MarkdownSourceViewState state, Consumer<MarkdownSourceViewState> applied,
                                UnaryOperator<String> text) {
        this.state = state;
        this.applied = applied;
        lineNumbers = new JCheckBox(text.apply("settings.sourceLineNumbers"));
        lineNumbers.setName("markdown.settings.lineNumbers");
        highlighting = new JCheckBox(text.apply("settings.syntaxHighlighting"));
        highlighting.setName("markdown.settings.syntaxHighlighting");
        reset();
    }

    @Override
    public JComponent createComponent() {
        JPanel rows = new JPanel(new GridLayout(0, 1));
        rows.add(lineNumbers);
        rows.add(highlighting);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(rows, BorderLayout.NORTH);
        return panel;
    }

    @Override
    public boolean isModified() {
        return lineNumbers.isSelected() != state.showLineNumbers()
                || highlighting.isSelected() != state.showSyntaxHighlighting();
    }

    @Override
    public void apply() {
        state.setShowLineNumbers(lineNumbers.isSelected());
        state.setShowSyntaxHighlighting(highlighting.isSelected());
        applied.accept(state);
    }

    @Override
    public void reset() {
        lineNumbers.setSelected(state.showLineNumbers());
        highlighting.setSelected(state.showSyntaxHighlighting());
    }
}
