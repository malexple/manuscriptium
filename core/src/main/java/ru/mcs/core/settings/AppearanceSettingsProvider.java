package ru.mcs.core.settings;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.theme.McsThemeManager;
import ru.mcs.spi.settings.Configurable;
import ru.mcs.spi.settings.ConfigurableProvider;
import ru.mcs.spi.settings.ConfigurationException;

/** Выбор темы в стиле IntelliJ: системная, встроенные и пользовательские JSON-темы. */
public final class AppearanceSettingsProvider implements ConfigurableProvider {

    public static final String ID = "core.settings.appearance";

    private final McsThemeManager themes;

    public AppearanceSettingsProvider(McsThemeManager themes) {
        this.themes = themes;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return Messages.get("settings.appearance");
    }

    @Override
    public Configurable createConfigurable() {
        return new AppearanceConfigurable(themes);
    }

    private record ThemeChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final class AppearanceConfigurable implements Configurable {

        private final McsThemeManager themes;
        private final JComboBox<ThemeChoice> theme;

        private AppearanceConfigurable(McsThemeManager themes) {
            this.themes = themes;
            this.theme = new JComboBox<>();
            this.theme.addItem(new ThemeChoice(AppSettings.SYSTEM_THEME, Messages.get("theme.system")));
            for (McsThemeManager.ThemeInfo info : themes.getThemes()) {
                String label = switch (info.id()) {
                    case "light" -> Messages.get("theme.light");
                    case "dark" -> Messages.get("theme.dark");
                    default -> info.name();
                };
                this.theme.addItem(new ThemeChoice(info.id(), label));
            }
            select(themes.selectedTheme());
        }

        @Override
        public JComponent createComponent() {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.add(new JLabel(Messages.get("theme.label")));
            row.add(theme);

            JPanel panel = new JPanel(new BorderLayout(0, 12));
            panel.add(row, BorderLayout.NORTH);
            JLabel files = new JLabel(Messages.get("theme.files", themes.directory()));
            files.setToolTipText(themes.directory().toString());
            panel.add(files, BorderLayout.CENTER);
            return panel;
        }

        @Override
        public boolean isModified() {
            return !selectedTheme().equals(themes.selectedTheme());
        }

        @Override
        public void apply() throws ConfigurationException {
            try {
                themes.setTheme(selectedTheme());
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException(e.getMessage(), e);
            }
        }

        @Override
        public void reset() {
            select(themes.selectedTheme());
        }

        private String selectedTheme() {
            return ((ThemeChoice) theme.getSelectedItem()).id();
        }

        private void select(String id) {
            for (int i = 0; i < theme.getItemCount(); i++) {
                if (theme.getItemAt(i).id().equals(id)) {
                    theme.setSelectedIndex(i);
                    return;
                }
            }
        }
    }
}
