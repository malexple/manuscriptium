package ru.mcs.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.service.AtomicFileStore;

class YamlFileEditorTest {

    @TempDir
    Path directory;

    private static final AtomicFileStore FILES = new AtomicFileStore() {
        @Override
        public byte[] read(Path file) throws IOException {
            return Files.readAllBytes(file);
        }

        @Override
        public void write(Path file, byte[] content) throws IOException {
            Files.write(file, content);
        }
    };

    private static YamlEditorServices services() {
        return new YamlEditorServices(FILES, I18n.passthrough(), new YamlSettings(), new YamlCodeStyleState(),
                YamlTokenColors::new, () -> null);
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static void awaitLoaded(YamlFileEditor editor) throws Exception {
        for (int attempt = 0; attempt < 250; attempt++) {
            boolean[] loaded = new boolean[1];
            onEdt(() -> loaded[0] = editor.isLoaded());
            if (loaded[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Editor was not loaded in time");
    }

    @Test
    void providerAcceptsOnlyYamlFilesCaseInsensitively() throws Exception {
        YamlFileEditorProvider provider = new YamlFileEditorProvider(services());
        Path yml = Files.writeString(directory.resolve("a.yml"), "a: 1\n");
        Path yaml = Files.writeString(directory.resolve("b.yaml"), "a: 1\n");
        Path upper = Files.writeString(directory.resolve("C.YML"), "a: 1\n");
        Path text = Files.writeString(directory.resolve("d.txt"), "a: 1\n");

        assertThat(provider.accept(yml)).isTrue();
        assertThat(provider.accept(yaml)).isTrue();
        assertThat(provider.accept(upper)).isTrue();
        assertThat(provider.accept(text)).isFalse();
        assertThat(provider.accept(directory.resolve("missing.yml"))).isFalse();
        assertThat(provider.accept(null)).isFalse();
        assertThat(provider.getPriority()).isGreaterThan(-100);
    }

    @Test
    void editorReportsProblemsFormatsAndSaves() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "a: 1  \nb: [\n", StandardCharsets.UTF_8);
        List<YamlFileEditor> disposed = new ArrayList<>();
        YamlFileEditor[] holder = new YamlFileEditor[1];
        onEdt(() -> holder[0] = new YamlFileEditor(file, services(), disposed::add));
        YamlFileEditor editor = holder[0];
        awaitLoaded(editor);

        onEdt(() -> {
            editor.applySettings();
            assertThat(editor.statusText()).contains("status.line");
            assertThat(editor.isModified()).isFalse();

            editor.document().apply(ru.mcs.editor.model.Replace.of(0, editor.document().length(), "a: 1  \nb: 2\n\n\n\n"));
            assertThat(editor.isModified()).isTrue();
            editor.validateNow();
            assertThat(editor.statusText()).isEqualTo("status.valid");

            editor.reformat();
            assertThat(editor.document().text().toString()).isEqualTo("a: 1\nb: 2\n");

            editor.canvas().setCaretSelection(0, editor.document().length());
            editor.toggleComment();
            assertThat(editor.document().text().toString()).isEqualTo("# a: 1\n# b: 2\n");
        });
        onEdt(() -> {
            try {
                editor.save();
            } catch (IOException error) {
                throw new AssertionError(error);
            }
            assertThat(editor.isModified()).isFalse();
            editor.dispose();
        });

        assertThat(Files.readString(file)).isEqualTo("# a: 1\n# b: 2\n");
        assertThat(disposed).containsExactly(editor);
    }
}
