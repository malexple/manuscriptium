package ru.mcs.spi.settings;

/** Ошибка применения настроек с сообщением, которое можно показать пользователю. */
public class ConfigurationException extends Exception {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
