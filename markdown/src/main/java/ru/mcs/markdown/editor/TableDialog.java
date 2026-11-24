package ru.mcs.markdown.editor;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import ru.mcs.markdown.widget.TableModel;

/** Диалог размера таблицы: количество колонок и строк (строка заголовка входит в число строк), ОК и Отмена. */
final class TableDialog {

    record Size(int columns, int rows) {
    }

    private TableDialog() {
    }

    static Optional<Size> show(Component owner, int columns, int rows) {
        JSpinner columnsSpinner = new JSpinner(new SpinnerNumberModel(
                clamp(columns, TableModel.MAX_COLUMNS), 1, TableModel.MAX_COLUMNS, 1));
        JSpinner rowsSpinner = new JSpinner(new SpinnerNumberModel(
                clamp(rows, TableModel.MAX_ROWS), 1, TableModel.MAX_ROWS, 1));
        columnsSpinner.setName("markdown.table.columns");
        rowsSpinner.setName("markdown.table.rows");

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        form.add(new JLabel(MarkdownMessages.get("table.dialog.columns")), constraints);
        constraints.gridx = 1;
        form.add(columnsSpinner, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        form.add(new JLabel(MarkdownMessages.get("table.dialog.rows")), constraints);
        constraints.gridx = 1;
        form.add(rowsSpinner, constraints);

        String ok = MarkdownMessages.get("table.dialog.ok");
        String cancel = MarkdownMessages.get("table.dialog.cancel");
        int choice = JOptionPane.showOptionDialog(owner, form, MarkdownMessages.get("table.dialog.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{ok, cancel}, ok);
        if (choice != 0) {
            return Optional.empty();
        }
        return Optional.of(new Size(value(columnsSpinner), value(rowsSpinner)));
    }

    private static int value(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (ParseException ignored) {
            // Некорректный ввод: остаётся последнее допустимое значение модели.
        }
        return ((Number) spinner.getValue()).intValue();
    }

    private static int clamp(int value, int max) {
        return Math.max(1, Math.min(max, value));
    }
}
