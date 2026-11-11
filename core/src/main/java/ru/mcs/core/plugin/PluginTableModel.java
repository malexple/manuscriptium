package ru.mcs.core.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.table.AbstractTableModel;
import ru.mcs.core.i18n.Messages;

/** Редактируемая модель реестра. Активное состояние меняется только после перезапуска приложения. */
final class PluginTableModel extends AbstractTableModel {

    private static final int ENABLED = 0;

    private final PluginState state;
    private final Map<String, Boolean> pending = new HashMap<>();
    private List<PluginDescriptor> descriptors = List.of();
    private Set<String> loaded = Set.of();
    private Map<String, String> failures = Map.of();

    PluginTableModel(PluginState state) {
        this.state = state;
    }

    void refresh(List<PluginDescriptor> discovered, Set<String> loaded, Map<String, String> failures) {
        descriptors = List.copyOf(discovered);
        this.loaded = Set.copyOf(loaded);
        this.failures = Map.copyOf(failures);
        fireTableDataChanged();
    }

    PluginDescriptor descriptorAt(int row) {
        return descriptors.get(row);
    }

    String failureAt(int row) {
        return failures.get(descriptorAt(row).id());
    }

    boolean isModified() {
        return descriptors.stream().anyMatch(d -> pending.getOrDefault(d.id(), state.isEnabled(d.id()))
                != state.isEnabled(d.id()));
    }

    /** @return true, если хотя бы одно состояние изменилось. */
    boolean applyChanges() {
        boolean changed = false;
        for (PluginDescriptor descriptor : descriptors) {
            String id = descriptor.id();
            changed |= state.setEnabled(id, pending.getOrDefault(id, state.isEnabled(id)));
        }
        pending.clear();
        fireTableDataChanged();
        return changed;
    }

    void resetChanges() {
        pending.clear();
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return descriptors.size();
    }

    @Override
    public int getColumnCount() {
        return 6;
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case 0 -> Messages.get("plugins.column.enabled");
            case 1 -> Messages.get("plugins.column.name");
            case 2 -> Messages.get("plugins.column.version");
            case 3 -> Messages.get("plugins.column.id");
            case 4 -> Messages.get("plugins.column.dependencies");
            case 5 -> Messages.get("plugins.column.status");
            default -> throw new IllegalArgumentException("Unknown column: " + column);
        };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == ENABLED ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == ENABLED;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        PluginDescriptor descriptor = descriptors.get(rowIndex);
        String id = descriptor.id();
        return switch (columnIndex) {
            case 0 -> pending.getOrDefault(id, state.isEnabled(id));
            case 1 -> descriptor.name();
            case 2 -> descriptor.version();
            case 3 -> id;
            case 4 -> String.join(", ", descriptor.dependsOn());
            case 5 -> status(descriptor);
            default -> throw new IllegalArgumentException("Unknown column: " + columnIndex);
        };
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (columnIndex != ENABLED || !(value instanceof Boolean enabled)) {
            return;
        }
        String id = descriptors.get(rowIndex).id();
        if (enabled == state.isEnabled(id)) {
            pending.remove(id);
        } else {
            pending.put(id, enabled);
        }
        fireTableRowsUpdated(rowIndex, rowIndex);
    }

    private String status(PluginDescriptor descriptor) {
        String id = descriptor.id();
        String failure = failures.get(id);
        if (failure != null) {
            return failure.startsWith(PluginManager.SDK_INCOMPATIBLE_FAILURE)
                    ? Messages.get("plugins.status.incompatible") : Messages.get("plugins.status.error");
        }
        if (!isCompatible(descriptor.sdkVersion())) {
            return Messages.get("plugins.status.incompatible");
        }
        if (pending.containsKey(id)) {
            return Messages.get("plugins.status.restart");
        }
        if (!state.isEnabled(id)) {
            return Messages.get("plugins.status.disabled");
        }
        if (loaded.contains(id)) {
            return Messages.get("plugins.status.active");
        }
        return Messages.get("plugins.status.enabled");
    }

    private static boolean isCompatible(String sdkVersion) {
        try {
            return SdkCompatibility.isCompatible(sdkVersion);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
