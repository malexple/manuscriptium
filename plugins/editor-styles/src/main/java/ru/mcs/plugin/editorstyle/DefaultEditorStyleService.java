package ru.mcs.plugin.editorstyle;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.style.ComputedStyle;
import ru.mcs.spi.editor.style.CssDiagnostic;
import ru.mcs.spi.editor.style.EditorStyleService;
import ru.mcs.spi.editor.style.EditorThemeInfo;
import ru.mcs.spi.editor.style.ElementState;
import ru.mcs.spi.editor.style.ShellThemeService;
import ru.mcs.spi.editor.style.StyleSheet;

/** Потокобезопасный snapshot-сервис; файловый ввод и Font.createFont выполняются вне EDT. */
final class DefaultEditorStyleService implements EditorStyleService, AutoCloseable {

    private final EditorStyleState state;
    private final ShellThemeService shell;
    private final Consumer<String> logger;
    private final TyporaCssLoader loader = new TyporaCssLoader();
    private final ThemeCatalog catalog;
    private final DefaultStyleResolver resolver = new DefaultStyleResolver();
    private final FontRegistry fontRegistry = new FontRegistry();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "manuscriptium-editor-styles");
        thread.setDaemon(true);
        return thread;
    });
    private final Consumer<ShellThemeService.Snapshot> themeListener = this::shellThemeChanged;

    private volatile boolean shellDark;
    private volatile boolean closed;
    private volatile StyleSheet styleSheet = StyleSheet.empty();
    private volatile String effectiveTheme;
    private volatile List<CssDiagnostic> fontDiagnostics = List.of();

    DefaultEditorStyleService(EditorStyleState state, ShellThemeService shell, Consumer<String> logger) {
        this.state = state;
        this.shell = shell;
        this.logger = logger;
        this.shellDark = shell.isDark();
        this.catalog = new ThemeCatalog(loader, shell.directory().resolveSibling("editor-themes"));
        this.effectiveTheme = effectiveSelection();
        shell.addThemeListener(themeListener);
    }

    void reloadAsync() {
        if (!closed) {
            executor.execute(this::reloadSafely);
        }
    }

    @Override
    public List<EditorThemeInfo> availableThemes() {
        return catalog.availableThemes();
    }

    @Override
    public String selectedTheme() {
        return effectiveTheme;
    }

    @Override
    public void selectTheme(String id) {
        state.theme(id);
        effectiveTheme = effectiveSelection();
        reloadAsync();
    }

    @Override
    public synchronized void reload() {
        if (closed) {
            return;
        }
        String id = effectiveSelection();
        ThemeCatalog.LoadedTheme loaded = catalog.load(id);
        styleSheet = loaded.sheet();
        effectiveTheme = loaded.info().id();
        fontRegistry.register(loaded.sheet(), loaded.themeDirectory());
        fontDiagnostics = fontRegistry.diagnostics();
        logger.accept("Editor theme '" + effectiveTheme + "': "
                + loaded.sheet().diagnostics().size() + " CSS diagnostic(s)");
        loaded.sheet().diagnostics().forEach(diagnostic -> logger.accept(
                diagnostic.level() + " " + diagnostic.source() + ":" + diagnostic.line()
                        + " " + diagnostic.message()));
    }

    @Override
    public StyleSheet styleSheet() {
        return styleSheet;
    }

    @Override
    public List<CssDiagnostic> diagnostics() {
        List<CssDiagnostic> result = new ArrayList<>(styleSheet.diagnostics());
        result.addAll(fontDiagnostics);
        result.addAll(resolver.diagnostics());
        return List.copyOf(result);
    }

    @Override
    public ComputedStyle resolve(BlockKind kind, List<String> classes, ElementState state) {
        return resolver.resolve(kind, classes, state, styleSheet);
    }

    @Override
    public Font font(ComputedStyle style, float sizePx) {
        return fontRegistry.font(style, sizePx);
    }

    private String effectiveSelection() {
        return EditorStyleState.AUTO.equals(state.theme()) ? catalog.defaultTheme(shellDark) : state.theme();
    }

    private void shellThemeChanged(ShellThemeService.Snapshot snapshot) {
        shellDark = snapshot.dark();
        if (EditorStyleState.AUTO.equals(state.theme())) {
            effectiveTheme = effectiveSelection();
            reloadAsync();
        }
    }

    private void reloadSafely() {
        try {
            reload();
        } catch (RuntimeException e) {
            // Неизвестная пользовательская тема не должна падать в фоновом потоке без журнала.
            fontDiagnostics = List.of(new CssDiagnostic("<theme>", 0, CssDiagnostic.Level.ERROR,
                    "Cannot reload editor theme: " + e.getMessage()));
        }
    }

    @Override
    public void close() {
        closed = true;
        shell.removeThemeListener(themeListener);
        executor.shutdownNow();
    }
}
