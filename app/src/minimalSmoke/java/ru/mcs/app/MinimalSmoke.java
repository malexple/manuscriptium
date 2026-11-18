package ru.mcs.app;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import ru.mcs.core.CoreBootstrap;
import ru.mcs.editor.model.DocumentModel;
import ru.mcs.editor.model.Replace;
import ru.mcs.editor.view.EditorCanvas;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.swing.editor.FileEditorManager;
import ru.mcs.spi.swing.editor.FileEditorProvider;

/** Реальный named-module smoke компоновки core + editor без основного app и markdown. */
public final class MinimalSmoke {

    private MinimalSmoke() {
    }

    public static void main(String[] args) throws Exception {
        check(ModuleLayer.boot().findModule("ru.mcs.core").isPresent(), "core is not resolved");
        check(ModuleLayer.boot().findModule("ru.mcs.editor").isPresent(), "editor is not resolved");
        check(ModuleLayer.boot().findModule("ru.mcs.markdown").isEmpty(), "markdown must be absent");
        check("ru.mcs.app".equals(MinimalSmoke.class.getModule().getName()), "minimal app must be a named module");
        checkPlatformDependencies();

        Path directory = Files.createTempDirectory("manuscriptium-minimal-smoke-");
        Path file = directory.resolve("plain.txt");
        Files.writeString(file, "alpha\n");
        List<String> providerIds = ServiceLoader.load(FileEditorProvider.class).stream()
                .map(ServiceLoader.Provider::get).map(FileEditorProvider::getId).toList();
        check(providerIds.equals(List.of("editor.text")),
                "Only the plain-text editor provider must be registered: " + providerIds);
        FileEditorManager editors = CoreBootstrap.createHeadlessEditorManager();
        try {
            AtomicReference<FileEditor> opened = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                check(editors.openFile(file), "No editor provider for plain.txt");
                opened.set(editors.selectedEditor().orElseThrow());
            });

            EditorCanvas canvas = findCanvas(opened.get().getComponent());
            DocumentModel document = awaitDocument(canvas);
            SwingUtilities.invokeAndWait(() -> {
                check(document.text().toString().equals("alpha\n"), "Unexpected initial text");
                document.apply(Replace.of(document.length(), document.length(), "beta\n"));
                check(document.text().toString().equals("alpha\nbeta\n"), "Insert failed");
                check(document.undo().canUndo(), "Undo is unavailable");
                document.undo().undo();
                check(document.text().toString().equals("alpha\n"), "Undo failed");
                check(document.undo().canRedo(), "Redo is unavailable");
                document.undo().redo();
                check(document.text().toString().equals("alpha\nbeta\n"), "Redo failed");
                check(opened.get().isModified(), "Editor must be modified before save");
            });

            AtomicReference<CompletableFuture<Void>> save = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> save.set(opened.get().saveAsync()));
            save.get().get(20, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> check(!opened.get().isModified(),
                    "Editor must be clean after completed save"));
            check(Files.readString(file).equals("alpha\nbeta\n"), "Saved file content mismatch");
            SwingUtilities.invokeAndWait(() -> editors.closeFile(file));

            System.out.println("MCS_MINIMAL_SMOKE modules=[ru.mcs.core, ru.mcs.editor] "
                    + "markdown=absent editorProviders=[editor.text] undoRedo=ok saved=plain.txt");
        } finally {
            SwingUtilities.invokeAndWait(() -> editors.closeFile(file));
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    /** Dependency test: языковой модуль меняет только компоновку app, но не JPMS-контракты платформы. */
    private static void checkPlatformDependencies() {
        for (String name : List.of("ru.mcs.spi", "ru.mcs.spi.swing", "ru.mcs.metadata",
                "ru.mcs.editor", "ru.mcs.core")) {
            Module module = ModuleLayer.boot().findModule(name).orElseThrow();
            check(module.getDescriptor().requires().stream()
                            .noneMatch(requires -> requires.name().equals("ru.mcs.markdown")),
                    name + " must not require the language module");
        }
    }

    private static DocumentModel awaitDocument(EditorCanvas canvas) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            AtomicReference<DocumentModel> document = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> document.set(canvas.document()));
            if (document.get() != null) {
                return document.get();
            }
            Thread.sleep(10);
        }
        AtomicReference<String> status = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> status.set(canvas.getStatus()));
        throw new AssertionError("Text document was not loaded: " + status.get());
    }

    private static EditorCanvas findCanvas(Component component) {
        if (component instanceof EditorCanvas canvas) {
            return canvas;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                EditorCanvas found = findCanvas(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
