package ru.mcs.yaml;

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
import javax.swing.SpinnerNumberModel;
import ru.mcs.spi.swing.settings.Configurable;

/** Editor → Code Style → YAML. */
final class YamlCodeStyleConfigurable implements Configurable {

    private final YamlCodeStyleState state;
    private final UnaryOperator<String> text;
    private final JSpinner indent = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
    private final JCheckBox tabs;
    private final JCheckBox trim;
    private final JCheckBox finalNewline;
    private final JSpinner maxBlank = new JSpinner(new SpinnerNumberModel(2, 0, 10, 1));

    YamlCodeStyleConfigurable(YamlCodeStyleState state, UnaryOperator<String> text) {
        this.state = state;
        this.text = text;
        indent.setName("yaml.codestyle.indent");
        maxBlank.setName("yaml.codestyle.maxBlank");
        tabs = box("yaml.codestyle.tabs", text.apply("codestyle.yaml.tabs"));
        trim = box("yaml.codestyle.trim", text.apply("codestyle.yaml.trim"));
        finalNewline = box("yaml.codestyle.finalNewline", text.apply("codestyle.yaml.finalNewline"));
        reset();
    }

    @Override
    public JComponent createComponent() {
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 6));
        rows.add(labeled("codestyle.yaml.indent", indent));
        rows.add(tabs);
        rows.add(trim);
        rows.add(finalNewline);
        rows.add(labeled("codestyle.yaml.maxBlank", maxBlank));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(rows, BorderLayout.NORTH);
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
        YamlStyle style = state.style();
        indent.setValue(style.indentSize());
        tabs.setSelected(style.tabsToSpaces());
        trim.setSelected(style.trimTrailingWhitespace());
        finalNewline.setSelected(style.finalNewline());
        maxBlank.setValue(style.maxBlankLines());
    }

    private YamlStyle read() {
        return new YamlStyle(intOf(indent), tabs.isSelected(), trim.isSelected(), finalNewline.isSelected(),
                intOf(maxBlank));
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

    private static JCheckBox box(String name, String label) {
        JCheckBox box = new JCheckBox(label);
        box.setName(name);
        return box;
    }
}
