package ru.mcs.core.settings;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.settings.Configurable;
import ru.mcs.spi.settings.ConfigurableProvider;

/** Общие настройки приложения: язык интерфейса, применяемый после перезапуска. */
public final class GeneralSettingsProvider implements ConfigurableProvider {

    public static final String ID = "core.settings.general";

    private final AppSettings settings;
    private Runnable restartRequired = () -> { };

    public GeneralSettingsProvider(AppSettings settings) {
        this.settings = settings;
    }

    public GeneralSettingsProvider(AppSettings settings, Runnable restartRequired) {
        this(settings);
        onRestartRequired(restartRequired);
    }

    /** Перезапуск — обязанность shell; headless CoreServices сохраняет страницу настроек без этого callback. */
    public void onRestartRequired(Runnable restartRequired) {
        this.restartRequired = java.util.Objects.requireNonNull(restartRequired);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return Messages.get("settings.general");
    }

    @Override
    public Configurable createConfigurable() {
        return new GeneralConfigurable(settings, restartRequired);
    }

    private record LanguageChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final class GeneralConfigurable implements Configurable {

        private final AppSettings settings;
        private final Runnable restartRequired;
        private final JComboBox<LanguageChoice> language = new JComboBox<>(new LanguageChoice[]{
                new LanguageChoice(AppSettings.AUTO, Messages.get("language.auto")),
                new LanguageChoice(AppSettings.EN, Messages.get("language.en")),
                new LanguageChoice(AppSettings.RU, Messages.get("language.ru"))
        });

        private GeneralConfigurable(AppSettings settings, Runnable restartRequired) {
            this.settings = settings;
            this.restartRequired = restartRequired;
            select(settings.language());
        }

        @Override
        public JComponent createComponent() {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.add(new JLabel(Messages.get("settings.language")));
            row.add(language);

            JPanel panel = new JPanel(new BorderLayout(0, 12));
            panel.add(row, BorderLayout.NORTH);
            panel.add(new JLabel(Messages.get("settings.restart")), BorderLayout.CENTER);
            return panel;
        }

        @Override
        public boolean isModified() {
            return !selectedLanguage().equals(settings.language());
        }

        @Override
        public void apply() {
            String selected = selectedLanguage();
            if (selected.equals(settings.language())) {
                return;
            }
            settings.setLanguage(selected);
            restartRequired.run();
        }

        @Override
        public void reset() {
            select(settings.language());
        }

        private String selectedLanguage() {
            return ((LanguageChoice) language.getSelectedItem()).id();
        }

        private void select(String id) {
            for (int i = 0; i < language.getItemCount(); i++) {
                if (language.getItemAt(i).id().equals(id)) {
                    language.setSelectedIndex(i);
                    return;
                }
            }
        }
    }
}
