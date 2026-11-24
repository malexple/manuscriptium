module ru.mcs.app {
    requires ru.mcs.core;
    requires ru.mcs.editor;
    requires ru.mcs.markdown;
    requires ru.mcs.search;

    // JDK-модули, которые должны попасть в runtime-образ для плагинов (иначе jlink их выкинет).
    requires java.net.http;
    requires java.sql;
    requires java.logging;
}
