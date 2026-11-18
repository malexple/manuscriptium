package ru.mcs.core.settings;

import java.util.Map;
import ru.mcs.core.action.CoreMetadataProvider;
import ru.mcs.core.i18n.Messages;
import ru.mcs.metadata.MetadataCatalog;
import ru.mcs.metadata.MetadataDocument;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.settings.ConfigurableProvider;

/** Общие настройки приложения: язык интерфейса, применяемый после перезапуска. */
public final class GeneralSettingsProvider implements ConfigurableProvider {

    public static final String ID = "core.settings.general";

    private final AppSettings settings;
    private final MetadataDocument.SettingsPage description;
    private Runnable restartRequired = () -> { };

    public GeneralSettingsProvider(AppSettings settings) {
        this(settings, loadDescription());
    }

    public GeneralSettingsProvider(AppSettings settings, Runnable restartRequired) {
        this(settings);
        onRestartRequired(restartRequired);
    }

    public GeneralSettingsProvider(AppSettings settings, MetadataDocument.SettingsPage description) {
        this.settings = settings;
        this.description = description;
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
        return Messages.get(description.titleKey());
    }

    @Override
    public Configurable createConfigurable() {
        MetadataFormConfigurable.ChoiceBinding language = new MetadataFormConfigurable.ChoiceBinding(
                settings::language, settings::setLanguage);
        return new MetadataFormConfigurable(description, Map.of("language", language),
                Messages::get, restartRequired);
    }

    private static MetadataDocument.SettingsPage loadDescription() {
        return MetadataCatalog.from(new CoreMetadataProvider()).get("core").document().settingsPages().stream()
                .filter(page -> page.id().equals(ID))
                .findFirst()
                .orElseThrow();
    }
}
