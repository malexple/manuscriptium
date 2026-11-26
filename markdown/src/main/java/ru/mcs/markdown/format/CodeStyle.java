package ru.mcs.markdown.format;

/**
 * Параметры форматирования Markdown.
 *
 * @param hardWrap         ширина переноса строк; 0 — не переносить
 * @param keepLineBreaks   не склеивать строки абзаца при переносе (только разбивать слишком длинные)
 * @param wrapQuotes       переносить текст внутри цитат
 * @param formatTables     выравнивать таблицы
 * @param maxBlankLines    сколько пустых строк подряд сохранять (минимум 1)
 * @param blankLinesAround сколько пустых строк гарантировать вокруг заголовков, блоков кода, формул, таблиц и линий
 */
public record CodeStyle(int hardWrap, boolean keepLineBreaks, boolean wrapQuotes, boolean formatTables,
                        int maxBlankLines, int blankLinesAround) {

    public CodeStyle {
        hardWrap = Math.max(0, Math.min(hardWrap, 1000));
        maxBlankLines = Math.max(1, Math.min(maxBlankLines, 10));
        blankLinesAround = Math.max(0, Math.min(blankLinesAround, 2));
    }

    public static CodeStyle defaults() {
        return new CodeStyle(0, true, true, true, 1, 1);
    }
}
