package ru.mcs.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.service.AtomicFileStore;

class CoreServicesAtomicFileStoreTest {

    @Test
    void registersAtomicFileStoreBeforePluginsStart(@TempDir Path home) {
        CoreServices services = new CoreServices(home, false);

        assertThat(services.services.find(AtomicFileStore.class)).isPresent();
    }
}
