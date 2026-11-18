package ru.mcs.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.service.StateManager;
import ru.mcs.spi.swing.BuiltInModule;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;

class CoreBuiltInModulesTest {

    @TempDir
    Path home;

    @Test
    void startsComposedModuleAndReleasesItsCapabilitiesOnStop() {
        CoreServices services = new CoreServices(home, false);
        AtomicBoolean stopped = new AtomicBoolean();
        BuiltInModule module = new BuiltInModule() {
            @Override
            public String id() {
                return "demo";
            }

            @Override
            public void start(PluginContext context) {
                context.actions().registerAction("demo.action", new AnAction("Demo") {
                    @Override
                    public void actionPerformed(AnActionEvent event) {
                    }
                });
                context.state().register("state", Scope.APPLICATION, new MapState());
            }

            @Override
            public void stop() {
                stopped.set(true);
            }
        };

        services.startBuiltIns(List.of(module));
        assertThat(services.builtInIds()).containsExactly("demo");
        assertThat(services.builtInFailures()).isEmpty();
        assertThat(services.actions.getAction("demo.action")).isNotNull();
        assertThat(services.state.ids()).contains("demo.state");

        services.stopBuiltIns();
        assertThat(stopped).isTrue();
        assertThat(services.builtInIds()).isEmpty();
        assertThat(services.actions.getAction("demo.action")).isNull();
        assertThat(services.state.ids()).doesNotContain("demo.state");
    }

    @Test
    void initializesBaseServicesWithoutProjectOrBuiltInModules() {
        CoreServices services = new CoreServices(home, false);

        assertThat(services.projectState.project()).isEmpty();
        assertThat(services.builtInIds()).isEmpty();
        assertThat(services.builtInFailures()).isEmpty();
        assertThat(services.editors.providerIds()).isEmpty();
        assertThat(services.actions.getAction("MainMenu")).isNotNull();
        assertThat(services.configurables.getProviders()).isNotEmpty();
    }

    @Test
    void failedModuleIsCleanedUpAndReported() {
        CoreServices services = new CoreServices(home, false);
        AtomicBoolean stopped = new AtomicBoolean();
        BuiltInModule broken = new BuiltInModule() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public void start(PluginContext context) throws IOException {
                context.actions().registerAction("broken.action", new AnAction("Broken") {
                    @Override
                    public void actionPerformed(AnActionEvent event) {
                    }
                });
                throw new IOException("expected");
            }

            @Override
            public void stop() {
                stopped.set(true);
            }
        };

        services.startBuiltIns(List.of(broken));
        assertThat(stopped).isTrue();
        assertThat(services.builtInIds()).isEmpty();
        assertThat(services.builtInFailures()).containsKey("broken");
        assertThat(services.actions.getAction("broken.action")).isNull();
    }

    private static final class MapState implements ru.mcs.spi.service.PersistentStateComponent {
        @Override
        public java.util.Map<String, Object> getState() {
            return java.util.Map.of();
        }

        @Override
        public void loadState(java.util.Map<String, Object> state) {
        }
    }
}
