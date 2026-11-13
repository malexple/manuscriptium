package ru.mcs.plugin.mdeditor.model;

import java.util.List;

/** Атомарная для undo транзакция; команды выполняются в порядке списка. */
public record Composite(List<EditCommand> commands) implements EditCommand {

    public Composite {
        commands = List.copyOf(commands);
    }

    public static Composite of(EditCommand... commands) {
        return new Composite(List.of(commands));
    }
}
