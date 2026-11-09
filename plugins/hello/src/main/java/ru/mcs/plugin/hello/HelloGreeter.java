package ru.mcs.plugin.hello;

import ru.mcs.spi.i18n.I18n;

/** Сервис плагина: публикуется в ServiceRegistry, другие плагины могут получить его по классу. */
public final class HelloGreeter {

    private final I18n i18n;

    public HelloGreeter(I18n i18n) {
        this.i18n = i18n;
    }

    public String greet(String about) {
        return i18n.get("say.greeting", about);
    }
}
