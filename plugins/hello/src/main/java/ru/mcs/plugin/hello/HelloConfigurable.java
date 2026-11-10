package ru.mcs.plugin.hello;

import java.awt.FlowLayout;
import java.text.ParseException;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.settings.Configurable;
import ru.mcs.spi.settings.ConfigurationException;

/** Страница настроек Hello: шаг, на который действие-счётчик увеличивает сохраняемое значение. */
final class HelloConfigurable implements Configurable {

    private final HelloState state;
    private final I18n i18n;
    private final JSpinner step;

    HelloConfigurable(HelloState state, I18n i18n) {
        this.state = state;
        this.i18n = i18n;
        this.step = new JSpinner(new SpinnerNumberModel(
                state.step(), HelloState.MIN_STEP, HelloState.MAX_STEP, 1));
    }

    @Override
    public JComponent createComponent() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel(i18n.get("settings.step")));
        panel.add(step);
        return panel;
    }

    @Override
    public boolean isModified() {
        return ((Number) step.getValue()).intValue() != state.step();
    }

    @Override
    public void apply() throws ConfigurationException {
        try {
            step.commitEdit();
        } catch (ParseException e) {
            throw new ConfigurationException(i18n.get("settings.step.invalid"), e);
        }
        state.setStep(((Number) step.getValue()).intValue());
    }

    @Override
    public void reset() {
        step.setValue(state.step());
    }
}
