package ru.mcs.yaml;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.function.UnaryOperator;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import ru.mcs.spi.swing.settings.Configurable;

/** Editor → General → YAML; открытые редакторы обновляются сразу после «Применить». */
final class YamlGeneralConfigurable implements Configurable {

    private final YamlSettings settings;
    private final Runnable applied;
    private final JCheckBox lineNumbers;
    private final JCheckBox highlighting;
    private final JCheckBox validate;
    private final JCheckBox strictKeys;

    YamlGeneralConfigurable(YamlSettings settings, Runnable applied, UnaryOperator<String> text) {
        this.settings = settings;
        this.applied = applied;
        lineNumbers = box("yaml.settings.lineNumbers", text.apply("settings.yaml.lineNumbers"));
        highlighting = box("yaml.settings.highlighting", text.apply("settings.yaml.highlighting"));
        validate = box("yaml.settings.validate", text.apply("settings.yaml.validate"));
        strictKeys = box("yaml.settings.strictKeys", text.apply("settings.yaml.strictKeys"));
        reset();
    }

    @Override
    public JComponent createComponent() {
        JPanel rows = new JPanel(new GridLayout(0, 1));
        rows.add(lineNumbers);
        rows.add(highlighting);
        rows.add(validate);
        rows.add(strictKeys);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(rows, BorderLayout.NORTH);
        return panel;
    }

    @Override
    public boolean isModified() {
        return lineNumbers.isSelected() != settings.lineNumbers() || highlighting.isSelected() != settings.highlighting()
                || validate.isSelected() != settings.validate() || strictKeys.isSelected() != settings.strictKeys();
    }

    @Override
    public void apply() {
        settings.set(lineNumbers.isSelected(), highlighting.isSelected(), validate.isSelected(),
                strictKeys.isSelected());
        applied.run();
    }

    @Override
    public void reset() {
        lineNumbers.setSelected(settings.lineNumbers());
        highlighting.setSelected(settings.highlighting());
        validate.setSelected(settings.validate());
        strictKeys.setSelected(settings.strictKeys());
    }

    private static JCheckBox box(String name, String text) {
        JCheckBox box = new JCheckBox(text);
        box.setName(name);
        return box;
    }
}
