module ru.mcs.plugin.hello {
    requires ru.mcs.spi;

    provides ru.mcs.spi.Plugin with ru.mcs.plugin.hello.HelloPlugin;
}
