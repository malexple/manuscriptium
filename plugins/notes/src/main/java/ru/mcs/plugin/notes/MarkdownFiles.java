package ru.mcs.plugin.notes;

import java.nio.file.Path;
import java.util.Locale;

/** Операции с Markdown-путями, не зависящие от UI и внутренних классов приложения. */
final class MarkdownFiles {

    private MarkdownFiles() {
    }

    static boolean isMarkdown(Path file) {
        return file != null && file.getFileName() != null
                && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    /** Короткая Obsidian-ссылка по имени файла; resolver приложения найдёт ближайший подходящий путь. */
    static String wikiLink(Path file) {
        if (!isMarkdown(file)) {
            throw new IllegalArgumentException("Not a Markdown file: " + file);
        }
        String name = file.getFileName().toString();
        return "[[" + name.substring(0, name.length() - 3) + "]]";
    }
}
