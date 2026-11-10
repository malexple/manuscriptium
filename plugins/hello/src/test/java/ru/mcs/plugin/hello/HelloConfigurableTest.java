package ru.mcs.plugin.hello;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.i18n.I18n;

class HelloConfigurableTest {

    @Test
    void editsAreAppliedOnlyOnApplyAndResetRestoresState() throws Exception {
        HelloState state = new HelloState();
        HelloConfigurable configurable = new HelloConfigurable(state, I18n.passthrough());
        JSpinner spinner = (JSpinner) ((JPanel) configurable.createComponent()).getComponent(1);

        spinner.setValue(5);
        assertThat(configurable.isModified()).isTrue();
        assertThat(state.step()).isEqualTo(1);

        configurable.reset();
        assertThat(spinner.getValue()).isEqualTo(1);
        assertThat(configurable.isModified()).isFalse();

        spinner.setValue(7);
        configurable.apply();
        state.increment();
        assertThat(state.step()).isEqualTo(7);
        assertThat(state.clicks()).isEqualTo(7);
        assertThat(configurable.isModified()).isFalse();
    }
}
