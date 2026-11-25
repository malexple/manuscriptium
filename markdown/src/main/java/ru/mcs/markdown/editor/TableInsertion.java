package ru.mcs.markdown.editor;

/** Где и с какими отступами вставить блок-таблицу: вокруг неё всегда ровно по одной пустой строке. */
public record TableInsertion(String replacement, int caretOffset) {

    /**
     * @param start начало заменяемого диапазона (выделение или каретка)
     * @param end   конец заменяемого диапазона
     * @param table исходный текст таблицы без отступов
     * @return текст замены и смещение каретки от {@code start}: первая ячейка заголовка
     */
    public static TableInsertion plan(String text, int start, int end, String table) {
        int before = start == 0 ? 2 : Math.min(2, trailingNewlines(text, start));
        int after = Math.min(2, leadingNewlines(text, end));
        String prefix = "\n".repeat(2 - before);
        String suffix = "\n".repeat(2 - after);
        return new TableInsertion(prefix + table + suffix, prefix.length() + 2);
    }

    private static int trailingNewlines(String text, int position) {
        int count = 0;
        while (position - count > 0 && text.charAt(position - count - 1) == '\n') {
            count++;
        }
        return count;
    }

    private static int leadingNewlines(String text, int position) {
        int count = 0;
        while (position + count < text.length() && text.charAt(position + count) == '\n') {
            count++;
        }
        return count;
    }
}
