package ru.mcs.markdown.editor;

import ru.mcs.markdown.editor.action.EditorActionIds;
import ru.mcs.markdown.editor.icons.MarkdownIcons;
import ru.mcs.markdown.notes.NotesModule;
import ru.mcs.markdown.properties.PropertiesModule;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.settings.ConfigurableProvider;
import ru.mcs.spi.swing.ui.ToolWindowSpec;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Регистрирует реализации; меню, parents и группы связывает DesktopMetadata по markdown.json. */
public final class MarkdownContributions {

    private MarkdownContributions() {
    }

    public static void register(PluginContext ctx, MarkdownEditorModule editor,
                                PropertiesModule properties, NotesModule notes) {
        ActionManager actions = ctx.actions();
        for (String id : EditorActionIds.ALL) {
            AnAction action = editor.createAction(id);
            if (action == null) {
                throw new IllegalStateException("Markdown action is not implemented: " + id);
            }
            if (EditorActionIds.TOGGLE_MODE.equals(id)) {
                action.getTemplatePresentation().setIcon(MarkdownIcons.load(
                        "/ru/mcs/markdown/editor/icons/notebook-text.svg"));
            }
            actions.registerAction(id, action);
        }

        AnAction copyWikiLink = notes.createAction("notes.copyWikiLink");
        copyWikiLink.getTemplatePresentation().setIcon(MarkdownIcons.load(
                "/ru/mcs/markdown/notes/icons/copy.svg"));
        actions.registerAction("notes.copyWikiLink", copyWikiLink);

        ctx.editors().registerProvider(editor.createFileEditorProvider());
        ctx.settings().register(new ConfigurableProvider() {
            @Override
            public String getId() {
                return "markdown.editor.settings";
            }

            @Override
            public String getDisplayName() {
                return ctx.i18n().get("settings.title");
            }

            @Override
            public Configurable createConfigurable() {
                return editor.createConfigurable();
            }
        });

        ctx.toolWindows().register(ToolWindowSpec
                .of("properties.panel", ctx.i18n().get("toolwindow.properties"), ToolWindowAnchor.RIGHT,
                        () -> properties.createToolWindowContent("properties.panel"))
                .withIcon(MarkdownIcons.load("/ru/mcs/markdown/properties/icons/sliders-horizontal.svg")));
        ctx.toolWindows().register(ToolWindowSpec
                .of("notes.panel", ctx.i18n().get("toolwindow.notes"), ToolWindowAnchor.LEFT,
                        () -> notes.createToolWindowContent("notes.panel"))
                .withIcon(MarkdownIcons.load("/ru/mcs/markdown/notes/icons/notebook-text.svg")));
    }
}
