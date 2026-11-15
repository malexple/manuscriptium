package ru.mcs.plugin.mdeditor.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import ru.mcs.plugin.mdeditor.layout.InlineContent;
import ru.mcs.spi.editor.BlockKind;
import ru.mcs.spi.editor.BlockRenderRequest;
import ru.mcs.spi.editor.BlockRendererExtension;
import ru.mcs.spi.editor.BlockRendererExtensionRegistry;
import ru.mcs.spi.editor.BlockSpan;
import ru.mcs.spi.editor.MathRenderingService;
import ru.mcs.spi.editor.RenderedBlock;
import ru.mcs.spi.editor.style.ComputedStyle;

/** Общие кэши и фабрика lightweight-виджетов; тяжёлая работа не выполняется на EDT. */
public final class WidgetRegistry implements BlockRendererExtensionRegistry {

    private final Executor executor;
    private final MathRenderingService math;
    private final ImageCache images;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<BlockRendererExtension> extensions = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<MathKey, AsyncImage> mathImages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ExtensionKey, AsyncImage> extensionImages = new ConcurrentHashMap<>();

    public WidgetRegistry(Executor executor, MathRenderingService math) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.math = math;
        images = new ImageCache(executor, this::fireChange);
    }

    public BlockWidget create(String raw, BlockSpan span, int end, int width, boolean dark,
                              ComputedStyle style, Font font, Path baseDirectory) {
        String source = raw.substring(span.start(), Math.max(span.start(), Math.min(end, raw.length())));
        return switch (span.kind()) {
            case MATH_BLOCK -> new MathWidget(span.start(), end, MathWidget.blockLatex(source),
                    true, font.getSize2D(), style.color(), this);
            case TABLE -> new TableWidget(span.start(), end, source, style, font);
            case FRONT_MATTER -> new FrontMatterWidget(span.start(), end, source, style, font);
            case CALLOUT -> new CalloutWidget(span.start(), end, source, style, font);
            case FENCED_CODE -> {
                String info = span.attributes().getOrDefault("info", "");
                BlockRendererExtension extension = extension(info);
                if (extension == null) {
                    yield null;
                }
                String code = fencedSource(source);
                yield new CodeBlockWidget(span.start(), end, new ExtensionKey(extension, info, code,
                        Math.max(1, width), dark, 1.0), this, style, font);
            }
            case PARAGRAPH, IMAGE, IMAGE_REFERENCE, WIKI_IMAGE, EMBED -> {
                ImageSource image = ImageSource.parseBlock(raw, span.start(), end);
                yield image == null ? null : new ImageWidget(span.start(), end, image,
                        baseDirectory, this, style, font);
            }
            default -> null;
        };
    }

    public InlineContent.Atom inlineMath(String latex, float fontSize, Color color) {
        MathKey key = new MathKey(latex, false, fontSize, color.getRGB());
        AsyncImage result = async(mathImages, key, () -> {
            if (math == null) {
                throw new IllegalStateException("JLaTeXMath plugin is not installed");
            }
            return math.render(latex, false, fontSize, color);
        });
        return new InlineContent.Atom(() -> result.image, latex);
    }

    AsyncImage math(String latex, boolean display, float fontSize, Color color) {
        MathKey key = new MathKey(latex, display, fontSize, color.getRGB());
        return async(mathImages, key, () -> {
            if (math == null) {
                throw new IllegalStateException("JLaTeXMath plugin is not installed");
            }
            return math.render(latex, display, fontSize, color);
        });
    }

    AsyncImage extension(ExtensionKey key) {
        return async(extensionImages, key, () -> {
            RenderedBlock rendered = key.extension.render(new BlockRenderRequest(key.info, key.source,
                    key.width, key.dark, key.scale)).join();
            return rendered.image();
        });
    }

    ImageCache.Entry image(Path path) {
        return images.get(path);
    }

    public Executor executor() {
        return executor;
    }

    public void addChangeListener(Runnable listener) {
        listeners.addIfAbsent(Objects.requireNonNull(listener, "listener"));
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public void register(BlockRendererExtension extension) {
        Objects.requireNonNull(extension, "extension");
        if (extension.supportedInfoStrings().isEmpty()) {
            throw new IllegalArgumentException("At least one info string is required");
        }
        extensions.addIfAbsent(extension);
        fireChange();
    }

    @Override
    public void unregister(BlockRendererExtension extension) {
        extensions.remove(extension);
        extensionImages.keySet().removeIf(key -> key.extension == extension);
        fireChange();
    }

    public int cachedMathCount() {
        return mathImages.size();
    }

    private BlockRendererExtension extension(String info) {
        String language = info == null ? "" : info.strip().split("\\s+", 2)[0];
        for (BlockRendererExtension extension : extensions) {
            Set<String> supported = extension.supportedInfoStrings();
            if (supported.contains(language) || supported.contains(info)) {
                return extension;
            }
        }
        return null;
    }

    private <K> AsyncImage async(ConcurrentHashMap<K, AsyncImage> cache, K key, ImageLoader loader) {
        AsyncImage result = cache.computeIfAbsent(key, ignored -> new AsyncImage());
        if (result.start()) {
            executor.execute(() -> {
                try {
                    result.image = loader.load();
                } catch (Throwable error) {
                    result.failed = true;
                } finally {
                    result.loading = false;
                    fireChange();
                }
            });
        }
        return result;
    }

    private void fireChange() {
        listeners.forEach(Runnable::run);
    }

    private static String fencedSource(String source) {
        int start = source.indexOf('\n');
        if (start < 0) {
            return "";
        }
        int end = source.length();
        while (end > start && (source.charAt(end - 1) == '\n' || source.charAt(end - 1) == '\r')) {
            end--;
        }
        int lastLine = source.lastIndexOf('\n', Math.max(start, end - 1));
        if (lastLine >= start && source.substring(lastLine + 1, end).strip().matches("(`|~){3,}")) {
            end = lastLine;
        }
        return source.substring(start + 1, Math.max(start + 1, end));
    }

    @FunctionalInterface
    private interface ImageLoader {
        BufferedImage load() throws Exception;
    }

    static final class AsyncImage {
        volatile BufferedImage image;
        volatile boolean failed;
        volatile boolean loading;

        private boolean start() {
            if (loading || image != null || failed) {
                return false;
            }
            loading = true;
            return true;
        }
    }

    private record MathKey(String latex, boolean display, float fontSize, int color) {
    }

    record ExtensionKey(BlockRendererExtension extension, String info, String source,
                        int width, boolean dark, double scale) {
    }
}
