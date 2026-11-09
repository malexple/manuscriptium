package ru.mcs.core.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.event.MessageBus;
import ru.mcs.spi.event.Topic;

class MessageBusImplTest {

    private static final Topic<PingListener> PING = Topic.create("test.ping", PingListener.class);

    private final MessageBusImpl bus = new MessageBusImpl();

    @Test
    void publishReachesAllSubscribers() {
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        bus.subscribe(PING, a::add);
        bus.subscribe(PING, b::add);
        bus.publisher(PING).ping("x");
        assertThat(a).containsExactly("x");
        assertThat(b).containsExactly("x");
    }

    @Test
    void unsubscribeStopsDelivery() {
        List<String> got = new ArrayList<>();
        Runnable unsubscribe = bus.subscribe(PING, got::add);
        bus.publisher(PING).ping("1");
        unsubscribe.run();
        bus.publisher(PING).ping("2");
        assertThat(got).containsExactly("1");
    }

    @Test
    void failingListenerDoesNotBlockOthers() {
        List<String> got = new ArrayList<>();
        bus.subscribe(PING, t -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(PING, got::add);
        bus.publisher(PING).ping("ok");
        assertThat(got).containsExactly("ok");
    }

    @Test
    void publishWithoutSubscribersIsHarmless() {
        bus.publisher(PING).ping("nobody");
    }

    @Test
    void scopedSubscriptionsAreRemovedWithOwner() {
        List<String> got = new ArrayList<>();
        MessageBus scoped = bus.scopedFor("p1");
        scoped.subscribe(PING, got::add);
        bus.publisher(PING).ping("before");
        bus.unsubscribeOwner("p1");
        bus.publisher(PING).ping("after");
        assertThat(got).containsExactly("before");
    }

    @Test
    void edtSubscriptionsRunOnEventDispatchThread() throws Exception {
        AtomicBoolean onEdt = new AtomicBoolean();
        bus.subscribeOnEdt(PING, t -> onEdt.set(SwingUtilities.isEventDispatchThread()));
        bus.publisher(PING).ping("x");
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(onEdt).isTrue();
    }

    @Test
    void topicsRequireInterfaces() {
        assertThatThrownBy(() -> Topic.create("bad", String.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listenerMethodsMustBeVoid() {
        Topic<java.util.function.Supplier> bad = Topic.create("bad", java.util.function.Supplier.class);
        assertThatThrownBy(() -> bus.publisher(bad)).isInstanceOf(IllegalArgumentException.class);
    }
}
