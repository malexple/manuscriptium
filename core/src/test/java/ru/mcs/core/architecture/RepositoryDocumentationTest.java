package ru.mcs.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Поддерживает самодостаточную документацию репозитория после удаления исторических планов. */
class RepositoryDocumentationTest {

    private static final Path ROOT = Path.of("").toAbsolutePath().normalize().getParent();
    private static final Pattern MARKDOWN_LINK = Pattern.compile("(?<!!)\\[[^]]+\\]\\(([^)]+)\\)");
    private static final List<String> OBSOLETE_FILES = List.of(
            "plan.md", "REQUIREMENTS.md", "TYPORA_FEATURES.md", "EDITOR_CANVAS_PLAN.md",
            "Manuscriptum.md", "manuscriptum.md", "prompt.md");

    @Test
    void currentDocumentsAndLicenseAreSelfContained() throws IOException {
        assertThat(ROOT.resolve("LICENSE")).isRegularFile().content().contains("MIT License");
        assertThat(ROOT.resolve("README.md")).isRegularFile();
        assertThat(ROOT.resolve("THIRD_PARTY_NOTICES.md")).isRegularFile();
        assertThat(ROOT.resolve("docs/plugin-sdk.md")).isRegularFile();
        assertThat(ROOT.resolve("plan_refactor.md")).isRegularFile();
        for (String name : OBSOLETE_FILES) {
            assertThat(ROOT.resolve(name)).as(name).doesNotExist();
        }
        try (var files = Files.newDirectoryStream(ROOT, "*.patch.txt")) {
            assertThat(files).isEmpty();
        }
        try (var files = Files.newDirectoryStream(ROOT, "Plan3*.md")) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void localMarkdownLinksResolve() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path document : currentDocuments()) {
            Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(document));
            while (matcher.find()) {
                String target = matcher.group(1).trim();
                if (target.startsWith("http://") || target.startsWith("https://")
                        || target.startsWith("mailto:") || target.startsWith("#")) {
                    continue;
                }
                int anchor = target.indexOf('#');
                if (anchor >= 0) {
                    target = target.substring(0, anchor);
                }
                if (target.isEmpty()) {
                    continue;
                }
                Path resolved = document.getParent().resolve(target).normalize();
                if (!Files.exists(resolved)) {
                    broken.add(ROOT.relativize(document) + " -> " + target);
                }
            }
        }
        assertThat(broken).isEmpty();
    }

    @Test
    void currentGuidesDoNotReferenceHistoricalPlansOrModules() {
        List<String> forbidden = List.of(
                "plan.md", "REQUIREMENTS.md", "TYPORA_FEATURES.md", "EDITOR_CANVAS_PLAN.md",
                "Manuscriptum.md", "manuscriptum.md", "prompt.md", "spi-editor", "editor-canvas",
                "desktop-swing", "lang-markdown-api", "product-minimal", "product-ai", "product-zig");
        List<String> offenders = new ArrayList<>();
        for (Path document : currentDocuments()) {
            if (document.getFileName().toString().equals("plan_refactor.md")) {
                continue;
            }
            String text = read(document);
            for (String token : forbidden) {
                if (text.contains(token)) {
                    offenders.add(ROOT.relativize(document) + ": " + token);
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    private static List<Path> currentDocuments() {
        return List.of(
                ROOT.resolve("README.md"),
                ROOT.resolve("THIRD_PARTY_NOTICES.md"),
                ROOT.resolve("plan_refactor.md"),
                ROOT.resolve("docs/plugin-sdk.md"),
                ROOT.resolve("plugin-template/README.md"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read " + path, error);
        }
    }
}
