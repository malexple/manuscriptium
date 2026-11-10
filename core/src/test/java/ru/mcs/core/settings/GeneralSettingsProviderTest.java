package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.settings.Configurable;

class GeneralSettingsProviderTest {

    @Test
    void selectionIsAppliedOnlyOnApplyAndResetRestoresSavedLanguage() throws Exception {
        AppSettings settings = new AppSettings();
        Configurable configurable = new GeneralSettingsProvider(settings).createConfigurable();
        JComboBox<?> language = languageCombo(configurable);

        assertThat(configurable.isModified()).isFalse();
        language.setSelectedIndex(2);
        assertThat(configurable.isModified()).isTrue();
        assertThat(settings.language()).isEqualTo(AppSettings.AUTO);

        configurable.reset();
        assertThat(language.getSelectedIndex()).isZero();
        assertThat(configurable.isModified()).isFalse();

        language.setSelectedIndex(1);
        configurable.apply();
        assertThat(settings.language()).isEqualTo(AppSettings.EN);
        assertThat(configurable.isModified()).isFalse();
    }

    private static JComboBox<?> languageCombo(Configurable configurable) {
        JPanel page = (JPanel) configurable.createComponent();
        JPanel row = (JPanel) page.getComponent(0);
        return (JComboBox<?>) row.getComponent(1);
    }
}
