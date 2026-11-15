package ru.mcs.spi.editor;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Асинхронный renderer fenced code block; реализация не обязана вызывать Swing. */
public interface BlockRendererExtension {

    Set<String> supportedInfoStrings();

    CompletableFuture<RenderedBlock> render(BlockRenderRequest request);
}
