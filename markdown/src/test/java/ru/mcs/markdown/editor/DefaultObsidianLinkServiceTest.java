package ru.mcs.markdown.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.markdown.api.ObsidianLinkService;

class DefaultObsidianLinkServiceTest {

    @TempDir
    Path root;

    @Test
    void resolvesMarkdownFragmentAndAttachmentThroughExistingProjectResolver() throws Exception {
        Path source = Files.writeString(root.resolve("source.md"), "source");
        Path chapter = Files.writeString(root.resolve("Chapter.md"), "# Part\nbody");
        Path image = Files.write(root.resolve("diagram.png"), new byte[]{1});
        ObsidianLinkNavigator navigator = new ObsidianLinkNavigator(new TestFileEditorManager());
        navigator.setProject(root, List.of(source, chapter));
        DefaultObsidianLinkService service = new DefaultObsidianLinkService(navigator);

        ObsidianLinkService.Target resolved = service.resolve(source, "Chapter#Part").join().orElseThrow();
        assertThat(resolved.file()).isEqualTo(chapter.toAbsolutePath().normalize());
        assertThat(resolved.fragment()).isEqualTo("Part");
        assertThat(resolved.markdown()).isTrue();

        ObsidianLinkService.Target attachment = service.resolve(source, "diagram.png").join().orElseThrow();
        assertThat(attachment.file()).isEqualTo(image.toAbsolutePath().normalize());
        assertThat(attachment.markdown()).isFalse();
        assertThat(service.resolve(source, "../outside.md").join()).isEmpty();
    }

}
