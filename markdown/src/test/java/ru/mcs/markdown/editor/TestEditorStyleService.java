package ru.mcs.markdown.editor;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.spi.swing.editor.style.ComputedStyle;
import ru.mcs.spi.swing.editor.style.CssBorder;
import ru.mcs.spi.swing.editor.style.CssBorders;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.editor.style.CssEdges;
import ru.mcs.spi.editor.style.CssLength;
import ru.mcs.markdown.api.EditorStyleService;
import ru.mcs.spi.editor.style.EditorThemeInfo;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.StyleSheet;
import ru.mcs.spi.editor.style.TextDecoration;

/** Детерминированные стили layout-тестов без файлового ввода и зависимости от editor-styles. */
public final class TestEditorStyleService implements EditorStyleService {

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private String selectedTheme = "test";

    @Override
    public List<EditorThemeInfo> availableThemes() {
        return List.of();
    }

    @Override
    public String selectedTheme() {
        return selectedTheme;
    }

    @Override
    public void selectTheme(String id) {
        selectedTheme = Objects.requireNonNull(id, "id");
        reload();
    }

    @Override
    public void reload() {
        listeners.forEach(Runnable::run);
    }

    @Override
    public StyleSheet styleSheet() {
        return StyleSheet.empty();
    }

    @Override
    public List<CssDiagnostic> diagnostics() {
        return List.of();
    }

    @Override
    public ComputedStyle resolve(BlockKind kind, List<String> classes, ElementState state) {
        ComputedStyle base = font(ComputedStyle.defaults(), 16, 400, List.of("SansSerif"));
        return switch (kind) {
            case DOCUMENT -> new ComputedStyle(base.color(), Color.WHITE, base.fontFamilies(), base.fontSize(),
                    base.fontWeight(), base.italic(), base.lineHeight(), base.textAlign(), base.textDecorations(),
                    base.margin(), CssEdges.all(CssLength.px(16)), base.borders(), base.borderRadius(),
                    CssLength.px(640), CssLength.px(10000), base.letterSpacing(), base.listStyleType(),
                    base.variables());
            case HEADING -> {
                int level = state.tag() != null && state.tag().matches("h[1-6]")
                        ? Integer.parseInt(state.tag().substring(1)) : 1;
                yield font(base, switch (level) {
                    case 1 -> 32;
                    case 2 -> 28;
                    case 3 -> 24;
                    case 4 -> 22;
                    case 5 -> 20;
                    default -> 18;
                }, 700, List.of("SansSerif"));
            }
            case BLOCK_QUOTE, CALLOUT -> block(base, new Color(0xf6f8fa),
                    new CssBorders(CssBorder.NONE, CssBorder.NONE, CssBorder.NONE,
                            new CssBorder(CssLength.px(4), "solid", new Color(0x8c959f))),
                    CssEdges.all(CssLength.px(8)), 0);
            case FENCED_CODE, INDENTED_CODE, FRONT_MATTER, MATH_BLOCK, TABLE, HTML_BLOCK, HTML_COMMENT ->
                    block(font(base, 14, 400, List.of("Monospaced")), new Color(0xf6f8fa), CssBorders.NONE,
                            CssEdges.all(CssLength.px(12)), 6);
            case CODE -> inline(font(base, 14, 400, List.of("Monospaced")), base.color(),
                    new Color(0xeff1f3), Set.of());
            case LINK, AUTOLINK, WIKILINK -> inline(base, new Color(0x0969da), base.backgroundColor(),
                    Set.of(TextDecoration.UNDERLINE));
            case THEMATIC_BREAK -> new ComputedStyle(base.color(), base.backgroundColor(), base.fontFamilies(),
                    base.fontSize(), base.fontWeight(), base.italic(), CssLength.px(4), base.textAlign(),
                    base.textDecorations(), base.margin(), base.padding(),
                    new CssBorders(CssBorder.NONE, CssBorder.NONE,
                            new CssBorder(CssLength.px(1), "solid", new Color(0x8c959f)), CssBorder.NONE),
                    base.borderRadius(), base.maxWidth(), base.width(), base.letterSpacing(), base.listStyleType(),
                    base.variables());
            default -> base;
        };
    }

    @Override
    public Font font(ComputedStyle style, float sizePx) {
        String family = style.fontFamilies().isEmpty() ? Font.SANS_SERIF : style.fontFamilies().getFirst();
        int awtStyle = (style.fontWeight() >= 600 ? Font.BOLD : Font.PLAIN)
                | (style.italic() ? Font.ITALIC : Font.PLAIN);
        return new Font(family, awtStyle, 12).deriveFont(sizePx);
    }

    @Override
    public void addChangeListener(Runnable listener) {
        listeners.addIfAbsent(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public int listenerCount() {
        return listeners.size();
    }

    private static ComputedStyle font(ComputedStyle base, double size, int weight, List<String> families) {
        return new ComputedStyle(base.color(), base.backgroundColor(), families, CssLength.px(size), weight,
                base.italic(), base.lineHeight(), base.textAlign(), base.textDecorations(), base.margin(),
                base.padding(), base.borders(), base.borderRadius(), base.maxWidth(), base.width(),
                base.letterSpacing(), base.listStyleType(), base.variables());
    }

    private static ComputedStyle inline(ComputedStyle base, Color color, Color background,
                                        Set<TextDecoration> decorations) {
        return new ComputedStyle(color, background, base.fontFamilies(), base.fontSize(), base.fontWeight(),
                base.italic(), base.lineHeight(), base.textAlign(), decorations, base.margin(), base.padding(),
                base.borders(), CssLength.px(4), base.maxWidth(), base.width(), base.letterSpacing(),
                base.listStyleType(), base.variables());
    }

    private static ComputedStyle block(ComputedStyle base, Color background, CssBorders borders,
                                       CssEdges padding, double radius) {
        return new ComputedStyle(base.color(), background, base.fontFamilies(), base.fontSize(), base.fontWeight(),
                base.italic(), base.lineHeight(), base.textAlign(), base.textDecorations(), base.margin(), padding,
                borders, CssLength.px(radius), base.maxWidth(), base.width(), base.letterSpacing(),
                base.listStyleType(), base.variables());
    }
}
