package ru.mcs.core.vault;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import ru.mcs.spi.service.PersistentStateComponent;

/** Последний открытый Vault — глобальная настройка; сессия и прочее Vault-состояние хранится уже внутри него. */
public final class VaultState implements PersistentStateComponent {

    private Path vault;

    public Optional<Path> vault() {
        return Optional.ofNullable(vault);
    }

    public void setVault(Path value) {
        vault = value == null ? null : VaultPaths.normalize(value);
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        if (vault != null) {
            state.put("last", vault.toString());
        }
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        vault = null;
        if (state.get("last") instanceof String value && !value.isBlank()) {
            try {
                vault = VaultPaths.normalize(Path.of(value));
            } catch (InvalidPathException ignored) {
                // Повреждённая запись не мешает запуску; пользователь выберет Vault заново.
            }
        }
    }
}
