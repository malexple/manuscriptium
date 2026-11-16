package ru.mcs.core.editor;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import ru.mcs.spi.editor.ObsidianLinkService;

/** Адаптер существующих ObsidianLinkResolver/ObsidianLinkNavigator к публичному SPI. */
public final class CoreObsidianLinkService implements ObsidianLinkService {

    private final ObsidianLinkNavigator navigator;

    public CoreObsidianLinkService(ObsidianLinkNavigator navigator) {
        this.navigator = java.util.Objects.requireNonNull(navigator, "navigator");
    }

    @Override
    public CompletableFuture<Optional<Target>> resolve(Path source, String target) {
        return CompletableFuture.supplyAsync(() -> resolveNow(source, target));
    }

    private Optional<Target> resolveNow(Path source, String target) {
        ObsidianSyntax.Link link = link(target, false);
        Path root = navigator.projectRoot();
        List<Path> notes = navigator.projectNotes();
        String name = link.target().substring(link.target().lastIndexOf('/') + 1);
        boolean markdown = link.target().isBlank() || !name.contains(".")
                || link.target().toLowerCase(Locale.ROOT).endsWith(".md");
        if (markdown) {
            return ObsidianLinkResolver.note(root, source, notes, link.target())
                    .map(path -> new Target(path, link.fragment(), true));
        }
        return ObsidianLinkResolver.attachment(root, source, link.target())
                .map(path -> new Target(path, null, false));
    }

    @Override
    public void open(Path source, String target, JComponent anchor) {
        navigator.open(source, link(target, false), anchor);
    }

    private static ObsidianSyntax.Link link(String value, boolean embed) {
        String destination = value == null ? "" : value.trim();
        int hash = destination.indexOf('#');
        String target = hash < 0 ? destination : destination.substring(0, hash);
        String fragment = hash < 0 ? null : destination.substring(hash + 1);
        return new ObsidianSyntax.Link(0, destination.length(), embed, target.trim(),
                fragment == null || fragment.isBlank() ? null : fragment.trim(), null);
    }
}
