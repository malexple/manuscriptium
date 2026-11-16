package ru.mcs.spi.swing.editor;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import ru.mcs.spi.editor.BlockRenderRequest;

/** Асинхронный renderer fenced code block; реализация не обязана вызывать Swing. */
public interface BlockRendererExtension {

    Set<String> supportedInfoStrings();

    CompletableFuture<RenderedBlock> render(BlockRenderRequest request);
}
