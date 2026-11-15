package ru.mcs.plugin.mdeditor.view;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;

/** Копирует dropped/clipboard images в assets без overwrite; файловый ввод выполняется вне EDT. */
final class ImageInputService {

    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    private final Executor executor;

    ImageInputService(Executor executor) {
        this.executor = executor;
    }

    void insert(EditorCanvas canvas, Path baseDirectory, int offset, List<Path> files, BufferedImage image) {
        if (baseDirectory == null || (files.isEmpty() && image == null)) {
            return;
        }
        long version = canvas.document() == null ? -1 : canvas.document().version();
        executor.execute(() -> {
            try {
                Path assets = baseDirectory.toAbsolutePath().normalize().resolve("assets");
                Files.createDirectories(assets);
                List<String> markdown = new ArrayList<>();
                for (Path file : files) {
                    if (isImage(file) && Files.isRegularFile(file)) {
                        Path copied = copyUnique(assets, file);
                        markdown.add(markdown(baseDirectory.toAbsolutePath().normalize(), copied));
                    }
                }
                if (image != null) {
                    Path target = unique(assets, "clipboard-image.png");
                    try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)) {
                        if (!ImageIO.write(image, "png", output)) {
                            throw new IOException("PNG writer is unavailable");
                        }
                    }
                    markdown.add(markdown(baseDirectory.toAbsolutePath().normalize(), target));
                }
                if (!markdown.isEmpty()) {
                    String insertion = String.join("\n", markdown);
                    if (canvas.document() != null && canvas.document().version() == version) {
                        // Вставка публикуется на EDT; version проверяется повторно внутри canvas.
                        javax.swing.SwingUtilities.invokeLater(() -> canvas.insertImageMarkdown(version, offset, insertion));
                    }
                }
            } catch (Exception error) {
                javax.swing.SwingUtilities.invokeLater(() -> canvas.setStatus("Cannot import image: " + error.getMessage()));
            }
        });
    }

    private static Path copyUnique(Path assets, Path source) throws IOException {
        Path target = unique(assets, source.getFileName().toString());
        try (InputStream input = Files.newInputStream(source);
             OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            input.transferTo(output);
        }
        return target;
    }

    private static Path unique(Path assets, String filename) {
        String safe = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        int dot = safe.lastIndexOf('.');
        String stem = dot > 0 ? safe.substring(0, dot) : safe;
        String extension = dot > 0 ? safe.substring(dot) : ".png";
        Path candidate = assets.resolve(safe).normalize();
        if (!candidate.startsWith(assets)) {
            throw new IllegalArgumentException("Invalid image filename");
        }
        for (int index = 1; Files.exists(candidate); index++) {
            candidate = assets.resolve(stem + "-" + index + extension).normalize();
        }
        return candidate;
    }

    private static String markdown(Path base, Path image) {
        return "![](" + base.relativize(image).toString().replace('\\', '/').replace(" ", "%20") + ")";
    }

    private static boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }
}
