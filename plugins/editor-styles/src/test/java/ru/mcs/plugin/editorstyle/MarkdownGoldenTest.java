package ru.mcs.plugin.editorstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.mcs.plugin.mdeditor.layout.BlockBox;
import ru.mcs.plugin.mdeditor.layout.InlineRun;
import ru.mcs.plugin.mdeditor.layout.LayoutResult;
import ru.mcs.plugin.mdeditor.layout.MarkdownLayoutEngine;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.plugin.mdeditor.model.Range;
import ru.mcs.plugin.mdeditor.render.MarkdownRenderer;
import ru.mcs.spi.editor.AstIndex;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.MarkdownParser.ParseMetrics;
import ru.mcs.spi.editor.MarkdownParser.ParseResult;
import ru.mcs.spi.swing.editor.style.ComputedStyle;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.swing.editor.style.EditorStyleService;
import ru.mcs.spi.editor.style.EditorThemeInfo;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.ShellThemeService;
import ru.mcs.spi.editor.style.StyleSheet;

class MarkdownGoldenTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 800;
    private static final int SCALE = 2;
    private static final Font EMBEDDED_FONT = loadFont();

    @EnabledOnOs(OS.LINUX)
    @ParameterizedTest
    @ValueSource(strings = {"github", "newsprint", "night", "pixyll", "whitey", "vue", "lapis", "onedark", "notion"})
    void linuxGoldenAtScaleTwo(String theme) throws Exception {
        BufferedImage actual = render(theme);
        String updateDirectory = System.getProperty("manuscriptium.golden.update");
        if (updateDirectory != null) {
            Path target = Path.of(updateDirectory).resolve(theme + ".png");
            Files.createDirectories(target.getParent());
            ImageIO.write(actual, "png", target.toFile());
            return;
        }

        String resource = "/golden/linux/" + theme + ".png";
        try (InputStream input = MarkdownGoldenTest.class.getResourceAsStream(resource)) {
            assertThat(input).as("missing golden %s; regenerate with "
                    + "-Dmanuscriptium.golden.update=plugins/editor-styles/src/test/resources/golden/linux", resource)
                    .isNotNull();
            BufferedImage expected = ImageIO.read(input);
            assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
            assertThat(actual.getHeight()).isEqualTo(expected.getHeight());
            BufferedImage diff = new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int differentPixels = 0;
            for (int y = 0; y < actual.getHeight(); y++) {
                for (int x = 0; x < actual.getWidth(); x++) {
                    int expectedRgb = expected.getRGB(x, y);
                    int actualRgb = actual.getRGB(x, y);
                    if (expectedRgb == actualRgb) {
                        int gray = (expectedRgb >> 16 & 0xff) / 3 + (expectedRgb >> 8 & 0xff) / 3
                                + (expectedRgb & 0xff) / 3;
                        diff.setRGB(x, y, new Color(gray, gray, gray, 80).getRGB());
                    } else {
                        differentPixels++;
                        diff.setRGB(x, y, Color.MAGENTA.getRGB());
                    }
                }
            }
            if (differentPixels != 0) {
                Path directory = projectPath("build/reports/golden-diff");
                Files.createDirectories(directory);
                ImageIO.write(actual, "png", directory.resolve(theme + "-actual.png").toFile());
                ImageIO.write(expected, "png", directory.resolve(theme + "-expected.png").toFile());
                ImageIO.write(diff, "png", directory.resolve(theme + "-diff.png").toFile());
            }
            assertThat(differentPixels).as("different pixels for %s; diff artifacts are in build/reports/golden-diff", theme)
                    .isZero();
        }
    }

    @Test
    void darkThemeUsesInheritedReadableTextColorForParagraphsListsAndCode() {
        Fixture fixture = fixture("night");
        try {
            LayoutResult result = fixture.engine().layout();
            Color rootColor = fixture.engine().foreground();
            assertThat(rootColor.getRGB()).isNotEqualTo(new Color(0x24292f).getRGB());
            for (int blockIndex : new int[]{1, 3, 4, 6, 7}) {
                assertThat(result.blocks().get(blockIndex).lines().stream()
                        .flatMap(line -> line.runs().stream())
                        .filter(run -> !run.text().isBlank())
                        .map(InlineRun::color))
                        .as("readable text in block %s", blockIndex)
                        .contains(rootColor);
            }
        } finally {
            fixture.service().close();
        }
    }

    @Test
    void embeddedFontMetricsStayWithinOnePhysicalPixelAtScaleTwo() {
        Fixture fixture = fixture("github");
        try {
            InlineRun run = fixture.engine().layout().blocks().stream().map(BlockBox::lines).flatMap(List::stream)
                    .flatMap(line -> line.runs().stream()).filter(candidate -> !candidate.text().isBlank())
                    .findFirst().orElseThrow();
            TextLayout expected = new TextLayout(run.text(), run.font(), new FontRenderContext(null, true, true));
            assertThat(run.advance() * SCALE).as("physical advance at scale=2")
                    .isCloseTo(expected.getAdvance() * SCALE, org.assertj.core.data.Offset.offset(1.0f));
        } finally {
            fixture.service().close();
        }
    }

    private static BufferedImage render(String theme) {
        Fixture fixture = fixture(theme);
        try {
            LayoutResult layout = fixture.engine().layout();
            assertThat(layout.height()).isLessThan(HEIGHT);
            BufferedImage image = new BufferedImage(WIDTH * SCALE, HEIGHT * SCALE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.scale(SCALE, SCALE);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                graphics.setColor(fixture.engine().background());
                graphics.fillRect(0, 0, WIDTH, HEIGHT);
                new MarkdownRenderer(fixture.engine()).paint(graphics, new Rectangle(0, 0, WIDTH, HEIGHT), new Range(0, 0));
            } finally {
                graphics.dispose();
            }
            return image;
        } finally {
            fixture.service().close();
        }
    }

    private static Fixture fixture(String theme) {
        String markdown = "# Heading\n\n"
                + "Paragraph with **bold**, *italic*, ~~deleted~~, `code`, [link](https://example.com).\n\n"
                + "> Quote\n> continued\n\n"
                + "- one\n  - nested\n- [ ] task\n- [x] done\n\n"
                + "1. first\n2. second\n\n"
                + "---\n\n"
                + "```java\n// comment\nclass Demo { String value = \"text\"; }\n```\n\n"
                + "Active **source**";
        int paragraph = markdown.indexOf("Paragraph");
        int quote = markdown.indexOf("> Quote");
        int bullets = markdown.indexOf("- one");
        int ordered = markdown.indexOf("1. first");
        int thematicBreak = markdown.indexOf("---");
        int code = markdown.indexOf("```java");
        int active = markdown.indexOf("Active");
        List<BlockSpan> blocks = List.of(
                new BlockSpan(BlockKind.HEADING, 0, paragraph - 2, 1, Map.of("level", "1")),
                new BlockSpan(BlockKind.PARAGRAPH, paragraph, quote - 2, 1),
                new BlockSpan(BlockKind.BLOCK_QUOTE, quote, bullets - 2, 1),
                new BlockSpan(BlockKind.BULLET_LIST, bullets, ordered - 2, 1),
                new BlockSpan(BlockKind.ORDERED_LIST, ordered, thematicBreak - 2, 1),
                new BlockSpan(BlockKind.THEMATIC_BREAK, thematicBreak, code - 2, 1),
                new BlockSpan(BlockKind.FENCED_CODE, code, active - 2, 1, Map.of("info", "java")),
                new BlockSpan(BlockKind.PARAGRAPH, active, markdown.length(), 1));
        List<BlockSpan> spans = new ArrayList<>();
        spans.add(new BlockSpan(BlockKind.DOCUMENT, 0, markdown.length(), 0));
        spans.addAll(blocks);
        AstIndex ast = new AstIndex(new Result(0, spans, new ParseMetrics(blocks.size(), 0, true)));
        DocumentModelImpl document = new DocumentModelImpl(markdown, DocumentProperties.utf8(LineEnding.LF, false));
        EditorStyleState state = new EditorStyleState();
        state.theme(theme);
        DefaultEditorStyleService service = new DefaultEditorStyleService(state, new TestShellTheme(), message -> { });
        service.reload();
        MarkdownLayoutEngine engine = new MarkdownLayoutEngine();
        engine.setContext(document, ast, new EmbeddedFontStyles(service));
        engine.setWidth(WIDTH);
        engine.setCaretOffset(active);
        return new Fixture(service, engine);
    }

    private static Font loadFont() {
        try (InputStream input = MarkdownGoldenTest.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
            if (input == null) {
                throw new IOException("Embedded Liberation Sans is missing");
            }
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Path projectPath(String relative) {
        Path current = Path.of("").toAbsolutePath();
        return current.getFileName().toString().equals("editor-styles")
                ? current.resolve(relative) : current.resolve("plugins/editor-styles").resolve(relative);
    }

    private record Fixture(DefaultEditorStyleService service, MarkdownLayoutEngine engine) {
    }

    private record Result(long version, List<BlockSpan> spans, ParseMetrics metrics) implements ParseResult {
    }

    private static final class EmbeddedFontStyles implements EditorStyleService {
        private final EditorStyleService delegate;

        private EmbeddedFontStyles(EditorStyleService delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<EditorThemeInfo> availableThemes() {
            return delegate.availableThemes();
        }

        @Override
        public String selectedTheme() {
            return delegate.selectedTheme();
        }

        @Override
        public void selectTheme(String id) {
            delegate.selectTheme(id);
        }

        @Override
        public void reload() {
            delegate.reload();
        }

        @Override
        public StyleSheet styleSheet() {
            return delegate.styleSheet();
        }

        @Override
        public List<CssDiagnostic> diagnostics() {
            return delegate.diagnostics();
        }

        @Override
        public ComputedStyle resolve(BlockKind kind, List<String> classes, ElementState state) {
            return delegate.resolve(kind, classes, state);
        }

        @Override
        public Font font(ComputedStyle style, float sizePx) {
            int awtStyle = (style.fontWeight() >= 600 ? Font.BOLD : Font.PLAIN)
                    | (style.italic() ? Font.ITALIC : Font.PLAIN);
            return EMBEDDED_FONT.deriveFont(awtStyle, sizePx);
        }

        @Override
        public void addChangeListener(Runnable listener) {
            delegate.addChangeListener(listener);
        }

        @Override
        public void removeChangeListener(Runnable listener) {
            delegate.removeChangeListener(listener);
        }
    }

    private static final class TestShellTheme implements ShellThemeService {
        @Override
        public String selectedTheme() {
            return "light";
        }

        @Override
        public boolean isDark() {
            return false;
        }

        @Override
        public Path directory() {
            return Path.of("build/test-shell-themes");
        }

        @Override
        public void addThemeListener(Consumer<Snapshot> listener) {
        }

        @Override
        public void removeThemeListener(Consumer<Snapshot> listener) {
        }
    }
}
