package ru.mcs.core.settings;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import ru.mcs.metadata.MetadataDocument;
import ru.mcs.spi.swing.settings.Configurable;

/** Минимальный Swing renderer settings metadata; состояние принадлежит существующим моделям. */
public final class MetadataFormConfigurable implements Configurable {

    private final MetadataDocument.SettingsPage description;
    private final Map<String, ChoiceBinding> choices;
    private final Function<String, String> messages;
    private final Runnable afterApply;
    private final List<ChoiceControl> controls = new ArrayList<>();

    public MetadataFormConfigurable(MetadataDocument.SettingsPage description,
                                    Map<String, ChoiceBinding> choices,
                                    Function<String, String> messages,
                                    Runnable afterApply) {
        this.description = description;
        this.choices = Map.copyOf(choices);
        this.messages = messages;
        this.afterApply = afterApply;
    }

    @Override
    public JComponent createComponent() {
        if (!controls.isEmpty()) {
            throw new IllegalStateException("Metadata form component is already created");
        }
        JPanel page = new JPanel(new BorderLayout(0, 12));
        JPanel fields = new JPanel(new java.awt.GridLayout(0, 1, 0, 12));
        String note = null;
        for (MetadataDocument.Field field : description.fields()) {
            if (field.type() != MetadataDocument.FieldType.CHOICE) {
                throw new IllegalStateException("Desktop field renderer is not implemented: " + field.type());
            }
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.add(new JLabel(messages.apply(field.labelKey())));
            ChoiceControl control = choice(field);
            controls.add(control);
            row.add(control.combo());
            fields.add(row);
            if (field.noteKey() != null) {
                note = messages.apply(field.noteKey());
            }
        }
        page.add(description.fields().size() == 1 ? fields.getComponent(0) : fields, BorderLayout.NORTH);
        if (note != null) {
            page.add(new JLabel(note), BorderLayout.CENTER);
        }
        return page;
    }

    @Override
    public boolean isModified() {
        return controls.stream().anyMatch(ChoiceControl::modified);
    }

    @Override
    public void apply() {
        List<ChoiceControl> changed = controls.stream().filter(ChoiceControl::modified).toList();
        changed.forEach(ChoiceControl::apply);
        if (!changed.isEmpty()) {
            afterApply.run();
        }
    }

    @Override
    public void reset() {
        controls.forEach(ChoiceControl::reset);
    }

    private ChoiceControl choice(MetadataDocument.Field field) {
        ChoiceBinding binding = choices.get(field.stateKey());
        if (binding == null) {
            throw new IllegalArgumentException("No state binding for " + description.id() + "." + field.stateKey());
        }
        OptionChoice[] options = field.options().stream()
                .map(option -> new OptionChoice(option.value(), messages.apply(option.labelKey())))
                .toArray(OptionChoice[]::new);
        return new ChoiceControl(new JComboBox<>(options), binding);
    }

    private record OptionChoice(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final class ChoiceControl {

        private final JComboBox<OptionChoice> combo;
        private final ChoiceBinding binding;

        private ChoiceControl(JComboBox<OptionChoice> combo, ChoiceBinding binding) {
            this.combo = combo;
            this.binding = binding;
            reset();
        }

        private JComboBox<OptionChoice> combo() {
            return combo;
        }

        private boolean modified() {
            return !selected().equals(binding.get().get());
        }

        private void apply() {
            binding.set().accept(selected());
        }

        private void reset() {
            String value = binding.get().get();
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (combo.getItemAt(i).value().equals(value)) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
        }

        private String selected() {
            return ((OptionChoice) combo.getSelectedItem()).value();
        }
    }

    public record ChoiceBinding(Supplier<String> get, Consumer<String> set) {
    }
}
