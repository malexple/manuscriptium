package ru.mcs.markdown.editor;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.text.ParseException;
import java.util.function.UnaryOperator;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import ru.mcs.markdown.format.CodeStyle;
import ru.mcs.spi.swing.settings.Configurable;

/** Editor → Code Style → Markdown: вкладки «Переносы» и «Пустые строки». */
final class MarkdownCodeStyleConfigurable implements Configurable {

    private final MarkdownCodeStyleState state;
    private final UnaryOperator<String> text;
    private final JSpinner hardWrap = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
    private final JCheckBox keepLineBreaks;
    private final JCheckBox wrapQuotes;
    private final JCheckBox formatTables;
    private final JSpinner maxBlankLines = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JSpinner blankLinesAround = new JSpinner(new SpinnerNumberModel(1, 0, 2, 1));

    MarkdownCodeStyleConfigurable(MarkdownCodeStyleState state, UnaryOperator<String> text) {
        this.state = state;
        this.text = text;
        hardWrap.setName("markdown.codestyle.hardWrap");
        maxBlankLines.setName("markdown.codestyle.maxBlankLines");
        blankLinesAround.setName("markdown.codestyle.blankLinesAround");
        keepLineBreaks = new JCheckBox(text.apply("codestyle.keepLineBreaks"));
        keepLineBreaks.setName("markdown.codestyle.keepLineBreaks");
        wrapQuotes = new JCheckBox(text.apply("codestyle.wrapQuotes"));
        wrapQuotes.setName("markdown.codestyle.wrapQuotes");
        formatTables = new JCheckBox(text.apply("codestyle.formatTables"));
        formatTables.setName("markdown.codestyle.formatTables");
        reset();
    }

    @Override
    public JComponent createComponent() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(text.apply("codestyle.tab.wrapping"), page(
                labeled("codestyle.hardWrap", hardWrap), keepLineBreaks, wrapQuotes, formatTables));
        tabs.addTab(text.apply("codestyle.tab.blank"), page(
                labeled("codestyle.maxBlankLines", maxBlankLines),
                labeled("codestyle.blankLinesAround", blankLinesAround)));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public boolean isModified() {
        return !read().equals(state.style());
    }

    @Override
    public void apply() {
        state.setStyle(read());
    }

    @Override
    public void reset() {
        CodeStyle style = state.style();
        hardWrap.setValue(style.hardWrap());
        keepLineBreaks.setSelected(style.keepLineBreaks());
        wrapQuotes.setSelected(style.wrapQuotes());
        formatTables.setSelected(style.formatTables());
        maxBlankLines.setValue(style.maxBlankLines());
        blankLinesAround.setValue(style.blankLinesAround());
    }

    private CodeStyle read() {
        return new CodeStyle(intOf(hardWrap), keepLineBreaks.isSelected(), wrapQuotes.isSelected(),
                formatTables.isSelected(), intOf(maxBlankLines), intOf(blankLinesAround));
    }

    private static int intOf(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (ParseException invalid) {
            // остаётся последнее корректное значение
        }
        return ((Number) spinner.getValue()).intValue();
    }

    private JComponent labeled(String key, JSpinner spinner) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(new JLabel(text.apply(key)));
        row.add(spinner);
        return row;
    }

    private static JComponent page(JComponent... rows) {
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 6));
        for (JComponent row : rows) {
            list.add(row);
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(list, BorderLayout.NORTH);
        return panel;
    }
}
