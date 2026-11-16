package ru.mcs.spi.swing.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.swing.Plugin;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.swing.editor.MathRenderingService;
import ru.mcs.spi.swing.editor.RenderedBlock;
import ru.mcs.spi.swing.editor.style.ComputedStyle;
import ru.mcs.spi.swing.editor.style.EditorStyleService;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.ui.StatusBarWidget;
import ru.mcs.spi.swing.ui.ToolWindowSpec;

/** Проверяет публичный API и JPMS-границу desktop-части SDK. */
class SpiSwingModuleTest {

    private static final Path MODULE_INFO = Path.of("build", "classes", "java", "main", "module-info.class");
    private static final List<Class<?>> CONTRACTS = List.of(
            Plugin.class, AnAction.class, FileEditor.class, MathRenderingService.class, RenderedBlock.class,
            ComputedStyle.class, EditorStyleService.class, Configurable.class, StatusBarWidget.class,
            ToolWindowSpec.class);

    @Test
    void publicSwingContractsUseSpiSwingPackages() {
        assertThat(CONTRACTS).allSatisfy(contract -> {
            assertThat(Modifier.isPublic(contract.getModifiers())).as(contract.getName()).isTrue();
            assertThat(contract.getPackageName()).as(contract.getName()).startsWith("ru.mcs.spi.swing");
        });
    }

    @Test
    void moduleExportsContractsAndTransitionallyRequiresBaseSpi() throws IOException {
        ModuleDescriptor descriptor;
        try (InputStream input = Files.newInputStream(MODULE_INFO)) {
            descriptor = ModuleDescriptor.read(input);
        }
        assertThat(descriptor.name()).isEqualTo("ru.mcs.spi.swing");
        assertThat(descriptor.requires()).anySatisfy(requires -> {
            assertThat(requires.name()).isEqualTo("java.desktop");
            assertThat(requires.modifiers()).contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE);
        }).anySatisfy(requires -> {
            assertThat(requires.name()).isEqualTo("ru.mcs.spi");
            assertThat(requires.modifiers()).contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE);
        });
        Set<String> exports = descriptor.exports().stream()
                .map(ModuleDescriptor.Exports::source)
                .collect(Collectors.toSet());
        assertThat(exports).containsAll(CONTRACTS.stream()
                .map(Class::getPackageName)
                .collect(Collectors.toSet()));
    }
}
