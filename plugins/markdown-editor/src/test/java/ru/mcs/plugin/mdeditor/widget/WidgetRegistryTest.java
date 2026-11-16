package ru.mcs.plugin.mdeditor.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.draw.BlockWidget;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockRenderRequest;
import ru.mcs.spi.swing.editor.BlockRendererExtension;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.swing.editor.RenderedBlock;
import ru.mcs.spi.swing.editor.style.ComputedStyle;

class WidgetRegistryTest {

    @Test
    void mathAndExtensionRenderingRunOutsideEdtAndInvalidate() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        AtomicReference<String> mathThread = new AtomicReference<>();
        AtomicReference<String> extensionThread = new AtomicReference<>();
        try {
            WidgetRegistry registry = new WidgetRegistry(executor, (latex, display, size, color) -> {
                mathThread.set(Thread.currentThread().getName());
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return new BufferedImage(30, 20, BufferedImage.TYPE_INT_ARGB);
            });
            registry.addChangeListener(completed::countDown);
            registry.register(new BlockRendererExtension() {
                @Override
                public Set<String> supportedInfoStrings() {
                    return Set.of("mermaid");
                }

                @Override
                public CompletableFuture<RenderedBlock> render(BlockRenderRequest request) {
                    extensionThread.set(Thread.currentThread().getName());
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                    return CompletableFuture.completedFuture(new RenderedBlock(
                            new BufferedImage(40, 24, BufferedImage.TYPE_INT_ARGB), "diagram"));
                }
            });
            SwingUtilities.invokeAndWait(() -> {
                BlockWidget math = registry.create("$$\nx^2\n$$", new BlockSpan(BlockKind.MATH_BLOCK, 0, 9, 1),
                        9, 500, false, ComputedStyle.defaults(), new Font(Font.SANS_SERIF, Font.PLAIN, 16), null);
                BlockWidget diagram = registry.create("```mermaid\ngraph TD; A-->B;\n```",
                        new BlockSpan(BlockKind.FENCED_CODE, 0, 31, 1, Map.of("info", "mermaid")),
                        31, 500, false, ComputedStyle.defaults(), new Font(Font.MONOSPACED, Font.PLAIN, 14), null);
                assertThat(math).isInstanceOf(MathWidget.class);
                assertThat(diagram).isInstanceOf(CodeBlockWidget.class);
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            SwingUtilities.invokeAndWait(() -> assertThat(SwingUtilities.isEventDispatchThread()).isTrue());
            release.countDown();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(mathThread.get()).doesNotContain("AWT-EventQueue");
            assertThat(extensionThread.get()).doesNotContain("AWT-EventQueue");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
