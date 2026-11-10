package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.theme.McsThemeManager;
import ru.mcs.spi.settings.Configurable;

class AppearanceSettingsProviderTest {

    @TempDir
    Path directory;

    @Test
    void themeChoiceUsesApplyAndResetAndChangesLafImmediately() throws Exception {
        AppSettings settings = new AppSettings();
        McsThemeManager manager = new McsThemeManager(settings, directory);
        Configurable configurable = new AppearanceSettingsProvider(manager).createConfigurable();
        JComboBox<?> combo = themeCombo(configurable);

        assertThat(combo.getItemCount()).isEqualTo(3);
        assertThat(configurable.isModified()).isFalse();
        combo.setSelectedIndex(2);
        assertThat(configurable.isModified()).isTrue();
        assertThat(settings.theme()).isEqualTo(AppSettings.SYSTEM_THEME);

        configurable.apply();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(settings.theme()).isEqualTo("dark");
        assertThat(manager.isDark()).isTrue();
        assertThat(configurable.isModified()).isFalse();

        combo.setSelectedIndex(0);
        configurable.reset();
        assertThat(combo.getSelectedIndex()).isEqualTo(2);
        assertThat(configurable.isModified()).isFalse();

        combo.setSelectedIndex(1);
        configurable.apply();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(settings.theme()).isEqualTo("light");
        assertThat(manager.isDark()).isFalse();
    }

    private static JComboBox<?> themeCombo(Configurable configurable) {
        JPanel page = (JPanel) configurable.createComponent();
        JPanel row = (JPanel) page.getComponent(0);
        return (JComboBox<?>) row.getComponent(1);
    }
}
