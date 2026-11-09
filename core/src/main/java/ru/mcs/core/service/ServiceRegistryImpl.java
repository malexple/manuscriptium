package ru.mcs.core.service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ru.mcs.spi.service.ServiceRegistry;

public final class ServiceRegistryImpl implements ServiceRegistry {

    private static final String CORE_OWNER = "core";

    private record Registration(String owner, Object instance) {
    }

    private final Map<Class<?>, Registration> services = new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> type, T instance) {
        register(CORE_OWNER, type, instance);
    }

    public <T> void register(String owner, Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        if (services.putIfAbsent(type, new Registration(owner, instance)) != null) {
            throw new IllegalArgumentException("Service already registered: " + type.getName());
        }
    }

    @Override
    public <T> T get(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException("No service registered: " + type.getName()));
    }

    @Override
    public <T> Optional<T> find(Class<T> type) {
        Registration r = services.get(type);
        return r == null ? Optional.empty() : Optional.of(type.cast(r.instance()));
    }

    public void unregisterOwner(String owner) {
        services.values().removeIf(r -> r.owner().equals(owner));
    }

    public ServiceRegistry scopedFor(String owner) {
        ServiceRegistryImpl self = this;
        return new ServiceRegistry() {
            @Override
            public <T> void register(Class<T> type, T instance) {
                self.register(owner, type, instance);
            }

            @Override
            public <T> T get(Class<T> type) {
                return self.get(type);
            }

            @Override
            public <T> Optional<T> find(Class<T> type) {
                return self.find(type);
            }
        };
    }
}
