package ru.mcs.plugin.hello;

/** Сервис плагина: публикуется в ServiceRegistry, другие плагины могут получить его по классу. */
public final class HelloGreeter {

    public String greet(String about) {
        return "Hello from a ModuleLayer plugin! (" + about + ")";
    }
}
