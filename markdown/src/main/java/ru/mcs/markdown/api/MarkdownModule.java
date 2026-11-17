package ru.mcs.markdown.api;

import ru.mcs.markdown.editor.DefaultObsidianLinkService;
import ru.mcs.markdown.editor.MarkdownContributions;
import ru.mcs.markdown.editor.MarkdownEditorModule;
import ru.mcs.markdown.editor.MarkdownFallbackEditorProvider;
import ru.mcs.markdown.editor.MarkdownMessages;
import ru.mcs.markdown.editor.ObsidianLinkNavigator;
import ru.mcs.markdown.math.JLaTeXMathRenderingService;
import ru.mcs.markdown.notes.NotesModule;
import ru.mcs.markdown.parser.FlexmarkMarkdownParser;
import ru.mcs.markdown.properties.PropertiesModule;
import ru.mcs.markdown.project.MarkdownIndexContributor;
import ru.mcs.markdown.project.MarkdownProjectServices;
import ru.mcs.markdown.style.DefaultEditorStyleService;
import ru.mcs.markdown.style.EditorStyleState;
import ru.mcs.markdown.api.MarkdownParser;
import ru.mcs.spi.editor.style.ShellThemeService;
import ru.mcs.spi.project.BranchStatisticsService;
import ru.mcs.spi.project.IndexContributor;
import ru.mcs.spi.project.IndexContributorRegistry;
import ru.mcs.spi.project.ProjectAccess;
import ru.mcs.spi.project.ProjectFileFactory;
import ru.mcs.spi.service.AtomicFileStore;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.swing.BuiltInModule;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.editor.MathRenderingService;
import ru.mcs.spi.swing.project.BookService;
import ru.mcs.markdown.api.EditorStyleService;

/** Единственная публичная точка компоновки встроенной Markdown-функциональности. */
public final class MarkdownModule implements BuiltInModule {

    private MarkdownEditorModule editor;
    private DefaultEditorStyleService styles;
    private IndexContributor indexContributor;
    private IndexContributorRegistry indexRegistry;

    @Override
    public String id() {
        return "markdown";
    }

    @Override
    public String i18nBaseName() {
        return "ru/mcs/markdown/messages/messages";
    }

    @Override
    public void start(PluginContext context) {
        MarkdownMessages.install(context.i18n());
        ObsidianLinkNavigator links = new ObsidianLinkNavigator(context.editors(),
                context.services().get(ProjectAccess.class));
        links.setStatusHandler(context.statusBar()::setMessage);
        context.services().register(ObsidianLinkService.class, new DefaultObsidianLinkService(links));
        context.editors().registerProvider(new MarkdownFallbackEditorProvider(links));
        indexRegistry = context.services().get(IndexContributorRegistry.class);
        indexContributor = new MarkdownIndexContributor();
        indexRegistry.register(indexContributor);
        MarkdownProjectServices projectServices = new MarkdownProjectServices(
                context.services().get(AtomicFileStore.class));
        context.services().register(ProjectFileFactory.class, projectServices);
        context.services().register(BookService.class, projectServices);
        context.services().register(BranchStatisticsService.class, projectServices);
        context.services().register(MarkdownParser.class, new FlexmarkMarkdownParser());

        EditorStyleState styleState = new EditorStyleState();
        context.state().register("style", Scope.APPLICATION, styleState);
        styles = new DefaultEditorStyleService(styleState, context.services().get(ShellThemeService.class), context::log);
        context.services().register(EditorStyleService.class, styles);
        styles.reloadAsync();

        context.services().register(MathRenderingService.class, new JLaTeXMathRenderingService());

        editor = new MarkdownEditorModule();
        editor.start(context);
        PropertiesModule properties = new PropertiesModule();
        properties.start(context);
        NotesModule notes = new NotesModule();
        notes.start(context);
        MarkdownContributions.register(context, editor, properties, notes);
        context.log("Built-in Markdown parser, editor, styles, math, properties and notes registered");
    }

    @Override
    public void stop() {
        if (indexContributor != null) {
            indexRegistry.unregister(indexContributor);
            indexContributor = null;
            indexRegistry = null;
        }
        if (editor != null) {
            editor.stop();
            editor = null;
        }
        if (styles != null) {
            styles.close();
            styles = null;
        }
    }
}
