package ru.mcs.spi.editor;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;

/**
 * JDK-only мост к Obsidian-навигации приложения. Разрешение путей и проверки Project выполняет реализация core;
 * плагин редактора не получает доступ к внутренним классам core.
 */
public interface ObsidianLinkService {

    CompletableFuture<Optional<Target>> resolve(Path source, String target);

    void open(Path source, String target, JComponent anchor);

    /** @param markdown true для заметки, false для attachment */
    record Target(Path file, String fragment, boolean markdown) {
        public Target {
            file = file.toAbsolutePath().normalize();
        }
    }
}
