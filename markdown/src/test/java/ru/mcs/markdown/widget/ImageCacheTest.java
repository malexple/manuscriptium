package ru.mcs.markdown.widget;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ImageCacheTest {

    @Test
    void delayedDecoderDoesNotBlockEdtAndPublishesImage() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch decoderEntered = new CountDownLatch(1);
        CountDownLatch releaseDecoder = new CountDownLatch(1);
        CountDownLatch loaded = new CountDownLatch(1);
        try {
            ImageCache cache = new ImageCache(executor, 1024 * 1024, path -> {
                decoderEntered.countDown();
                assertThat(releaseDecoder.await(5, TimeUnit.SECONDS)).isTrue();
                return new BufferedImage(12, 8, BufferedImage.TYPE_INT_ARGB);
            }, loaded::countDown);
            ImageCache.Entry entry = cache.get(Path.of("delayed.png"));
            assertThat(decoderEntered.await(5, TimeUnit.SECONDS)).isTrue();

            long started = System.nanoTime();
            SwingUtilities.invokeAndWait(() -> assertThat(entry.loading()).isTrue());
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(1000);

            releaseDecoder.countDown();
            assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(entry.image().getWidth()).isEqualTo(12);
            assertThat(entry.failed()).isFalse();
            cache.dispose();
        } finally {
            releaseDecoder.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void lruIsBoundedByDecodedRgbaBytes() {
        ImageCache cache = new ImageCache(Runnable::run, 4L * 10 * 10,
                path -> new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB), () -> { });
        cache.get(Path.of("one.png"));
        cache.get(Path.of("two.png"));
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.bytes()).isEqualTo(400);
    }
}
