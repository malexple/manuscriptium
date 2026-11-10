package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.mcs.core.state.StateYaml;

class AppSettingsTest {

    private static final Locale RU = Locale.of("ru");

    @Test
    void autoFollowsSystemLanguageAndDefaultsToEnglish() {
        assertThat(AppSettings.resolveLocale(AppSettings.AUTO, RU)).isEqualTo(RU);
        assertThat(AppSettings.resolveLocale(AppSettings.AUTO, Locale.of("ru", "RU"))).isEqualTo(RU);
        assertThat(AppSettings.resolveLocale(AppSettings.AUTO, Locale.GERMAN)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void explicitChoiceWinsOverSystem() {
        assertThat(AppSettings.resolveLocale(AppSettings.EN, RU)).isEqualTo(Locale.ENGLISH);
        assertThat(AppSettings.resolveLocale(AppSettings.RU, Locale.GERMAN)).isEqualTo(RU);
    }

    @Test
    void stateSurvivesYamlRoundTrip() {
        AppSettings first = new AppSettings();
        first.setLanguage(AppSettings.RU);
        first.setTheme("dark");
        AppSettings second = new AppSettings();
        second.loadState(StateYaml.parse(StateYaml.dump(first.getState())));
        assertThat(second.language()).isEqualTo(AppSettings.RU);
        assertThat(second.theme()).isEqualTo("dark");
    }

    @Test
    void invalidValuesAreRejectedOrIgnored() {
        AppSettings settings = new AppSettings();
        assertThatThrownBy(() -> settings.setLanguage("klingon")).isInstanceOf(IllegalArgumentException.class);
        settings.loadState(Map.of("language", "klingon"));
        assertThat(settings.language()).isEqualTo(AppSettings.AUTO);
        settings.loadState(Map.of("language", 5));
        assertThat(settings.language()).isEqualTo(AppSettings.AUTO);
        assertThatThrownBy(() -> settings.setTheme(" ")).isInstanceOf(IllegalArgumentException.class);
        settings.loadState(Map.of("theme", " "));
        assertThat(settings.theme()).isEqualTo(AppSettings.SYSTEM_THEME);
    }
}
