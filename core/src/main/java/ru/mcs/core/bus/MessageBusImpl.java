package ru.mcs.core.bus;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import ru.mcs.spi.event.MessageBus;
import ru.mcs.spi.event.Topic;

public final class MessageBusImpl implements MessageBus {

    private static final System.Logger LOG = System.getLogger(MessageBusImpl.class.getName());

    private static final class Subscription {
        final Object listener;
        final boolean onEdt;

        Subscription(Object listener, boolean onEdt) {
            this.listener = listener;
            this.onEdt = onEdt;
        }
    }

    private final Map<Topic<?>, List<Subscription>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, List<Runnable>> ownerSubscriptions = new ConcurrentHashMap<>();

    @Override
    public <L> Runnable subscribe(Topic<L> topic, L listener) {
        return add(topic, new Subscription(listener, false));
    }

    @Override
    public <L> Runnable subscribeOnEdt(Topic<L> topic, L listener) {
        return add(topic, new Subscription(listener, true));
    }

    private Runnable add(Topic<?> topic, Subscription subscription) {
        subscriptions.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(subscription);
        return () -> {
            List<Subscription> list = subscriptions.get(topic);
            if (list != null) {
                list.remove(subscription);
            }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <L> L publisher(Topic<L> topic) {
        Class<L> type = topic.listenerClass();
        for (Method m : type.getMethods()) {
            if (m.getReturnType() != void.class) {
                throw new IllegalArgumentException("Listener methods must return void: " + m);
            }
        }
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "Publisher(" + topic.name() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            for (Subscription s : subscriptions.getOrDefault(topic, List.of())) {
                dispatch(s, method, args);
            }
            return null;
        };
        return (L) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static void dispatch(Subscription s, Method method, Object[] args) {
        Runnable call = () -> {
            try {
                method.invoke(s.listener, args);
            } catch (InvocationTargetException e) {
                LOG.log(System.Logger.Level.ERROR, "Message listener failed: " + method.getName(), e.getCause());
            } catch (Throwable t) {
                LOG.log(System.Logger.Level.ERROR, "Message dispatch failed: " + method.getName(), t);
            }
        };
        if (s.onEdt && !SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(call);
        } else {
            call.run();
        }
    }

    /** Шина для плагина: подписки запоминаются и снимаются при его выгрузке. */
    public MessageBus scopedFor(String owner) {
        MessageBusImpl self = this;
        return new MessageBus() {
            @Override
            public <L> Runnable subscribe(Topic<L> topic, L listener) {
                return track(self.subscribe(topic, listener));
            }

            @Override
            public <L> Runnable subscribeOnEdt(Topic<L> topic, L listener) {
                return track(self.subscribeOnEdt(topic, listener));
            }

            @Override
            public <L> L publisher(Topic<L> topic) {
                return self.publisher(topic);
            }

            private Runnable track(Runnable unsubscribe) {
                ownerSubscriptions.computeIfAbsent(owner, k -> new CopyOnWriteArrayList<>()).add(unsubscribe);
                return unsubscribe;
            }
        };
    }

    public void unsubscribeOwner(String owner) {
        List<Runnable> list = ownerSubscriptions.remove(owner);
        if (list != null) {
            list.forEach(Runnable::run);
        }
    }
}
