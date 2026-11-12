# План: собственный Canvas-редактор Markdown (Typora-совместимые темы)

> Адресат: ИИ-разработчик (KIMI), видящий репозиторий Manuscriptium. Задача: реализовать план по фазам без уточняющих вопросов. Перечень функций — в `TYPORA_FEATURES.md` (идентификаторы `F-xxx`). Если репозиторий расходится с этим документом (имена модулей, пакетов, существующих классов), следуй репозиторию и зафиксируй расхождение в разделе «Принятые решения» отчёта фазы.

## 0. Цель

Редактор в стиле Typora: один холст, где вне каретки Markdown отрисован, а блок с кареткой показан как исходник. Визуально управляется темами Typora (`.css`) в рамках согласованного подмножества CSS. Архитектура как у редактора IntelliJ IDEA: собственный `JComponent`, свой буфер текста, своя раскладка, свои каретка, выделение, undo/redo.

Критерий «почти так же»: тема открывается без ошибок, цвета, шрифты, отступы, границы и фоны применены; неподдерживаемое безопасно игнорируется и пишется в журнал. Пиксельное совпадение — не цель.

## 1. Строгие ограничения (Strict Constraints)

Те же, что в разделе 0 `TYPORA_FEATURES.md`. Дополнительно:
- Каждая фаза сдаётся полными файлами, с тестами `JUnit 5` + `AssertJ`, headless (раскладку и модель тестировать без показа окна).
- Код соответствует `module-info.java` существующих модулей; открывать пакеты только по необходимости.
- Все публичные контракты, доступные плагинам, — в `spi`, версионируются semver; `record` в публичном API — осознанно.
- Строки UI — через `MessageBundle` (ru/en), иконки — через `Icons.load`, действия — через `ActionManager`.

## 2. Архитектура и размещение

Реализация — в новом bundled-плагине `plugins/markdown-editor`, пакет `ru.mcs.plugin.mdeditor`, подключается через `FileEditorProvider` (как существующий `TextFileEditorProvider`, но с более высоким приоритетом для `.md`). Canvas и Source находятся внутри одной существующей Markdown-вкладки; отдельное окно предпросмотра и вторая вкладка не создаются. Контракты расширения — в `ru.mcs.spi.editor`. Если в репозитории уже принято иное размещение редактора, следовать ему.

`flexmark-java`, CSS-движок и `JLaTeXMath` поставляются как самостоятельные JAR-плагины. `markdown-editor` объявляет их в `depends-on` и обращается к ним через JDK-only сервисные контракты SPI; типы сторонних библиотек в SPI не попадают. Повторное использование Obsidian-навигации и preview из `core` выполняется через SPI-сервисы и адаптер в `core`: зависимость плагина от `core` не добавляется.

### 2.1. Слои (сверху вниз)

```
ru.mcs.plugin.mdeditor
 ├─ view/       EditorCanvas, CaretPainter, SelectionPainter, GutterPainter, ViewportController
 ├─ input/      InputController, KeyBindingResolver, MouseController, ImeSupport, ClipboardService
 ├─ model/      DocumentModel, TextBuffer(PieceTable), Position, Range, EditCommand, UndoManagerImpl
 ├─ parse/      MarkdownParser, AstIndex, BlockSpan, ParserExtensions(Obsidian, Math, FrontMatter, Footnote)
 ├─ layout/     LayoutEngine, BlockBox, LineBox, InlineRun, LayoutCache, TextMeasurer
 ├─ style/      CssParser, StyleSheet, StyleResolver, ComputedStyle, TyporaCssLoader, ThemeCatalog, FontRegistry
 ├─ render/     BlockRenderer, InlineRenderer, SourceBlockRenderer, WidgetRegistry, ImageCache
 ├─ widgets/    TableWidget, MathWidget, CodeBlockWidget, ImageWidget, CalloutWidget, FrontMatterWidget, TransclusionWidget
 ├─ mode/       EditorMode(SOURCE, WYSIWYG), FocusMode, TypewriterMode
 └─ menu/       EditorContextMenuBuilder, EditorContext, EditorActionIds
ru.mcs.spi.editor
 ├─ BlockRendererExtension, RenderedBlock, BlockRenderRequest
 ├─ EditorThemeContribution, EditorCommandContribution
 └─ DocumentSnapshot (read-only view для плагинов)
```

### 2.2. Ключевые контракты (сигнатуры без тел)

```java
// ru.mcs.plugin.mdeditor.model
public interface DocumentModel {
    CharSequence text();
    int length();
    long version();
    void apply(EditCommand cmd);              // единственная точка изменения
    void addListener(DocumentListener l);     // события: DocumentChange(start, oldLen, newLen, version)
    UndoManager undo();
}
public record Position(int line, int column) {}
public record Range(int start, int end) {}
public sealed interface EditCommand permits Insert, Delete, Replace, Composite {}

// ru.mcs.plugin.mdeditor.parse
public interface MarkdownParser {
    ParseResult parse(CharSequence text);                       // полный
    ParseResult reparse(ParseResult prev, DocumentChange ch);   // инкрементальный по блокам
}
public record BlockSpan(BlockKind kind, int start, int end, int depth, Object payload) {}

// ru.mcs.plugin.mdeditor.layout
public interface LayoutEngine {
    LayoutResult layout(ParseResult ast, StyleSheet style, int viewportWidth, LayoutContext ctx);
    LayoutResult relayout(LayoutResult prev, Set<BlockSpan> dirty, int viewportWidth);
    HitResult hitTest(LayoutResult r, int x, int y);            // координаты -> смещение
    Rectangle caretRect(LayoutResult r, int offset);            // смещение -> координаты
}

// ru.mcs.plugin.mdeditor.style
public interface TyporaCssLoader {
    StyleSheet load(Path themeCss, Optional<Path> userCss);     // никогда не бросает на неподдержанном
    List<CssDiagnostic> diagnostics();                          // для журнала (F-089)
}
public interface StyleResolver {
    ComputedStyle resolve(BlockKind kind, List<String> classes, ElementState state, StyleSheet sheet);
}

// ru.mcs.spi.editor (без внешних зависимостей)
public interface BlockRendererExtension {
    Set<String> supportedInfoStrings();                         // например "mermaid"
    CompletableFuture<RenderedBlock> render(BlockRenderRequest req); // вне EDT
}
public record BlockRenderRequest(String infoString, String source, int widthPx, boolean dark, double scale) {}
public record RenderedBlock(java.awt.image.BufferedImage image, String altText) {}
```

### 2.3. Потоки
- `EDT`: только рисование, ввод, применение готовых результатов.
- Фоновый пул (`ExecutorService` из оболочки): парсинг больших файлов, раскладка вне видимой области (опционально), декодирование картинок, `JLaTeXMath`, внешние рендереры. Результат возвращается в `EDT` через `SwingUtilities.invokeLater` с проверкой `DocumentModel.version()`.

## 3. Принципы рендеринга

1. Источник истины — `TextBuffer`. Отрисованный вид — производный, не сохраняется.
2. Блок с кареткой (или весь документ в `SOURCE`) → `SourceBlockRenderer` (моноширинный/из темы, без скрытия). Остальные → `BlockRenderer`.
3. Скрытие инлайн-маркеров: маркеры получают ширину 0, но остаются в модели; навигация стрелками проходит через них, при подходе каретки блок переходит в исходник.
4. `LayoutCache` по `BlockSpan` + ширине + версии стилей; при правке инвалидируются только затронутые блоки и их `y`-смещения.
5. Рисование — только видимые блоки; подготовленные картинки кэшируются в `ImageCache` (LRU по байтам).
6. Текст — `java.awt.font.TextLayout` / `LineBreakMeasurer`, включая двунаправленность и хит-тестинг.

## 4. Подмножество CSS темы Typora

Поддержать:
- Селекторы: тип (`h1`…`h6`, `p`, `blockquote`, `code`, `pre`, `a`, `table`, `th`, `td`, `tr`, `hr`, `ul`, `ol`, `li`, `img`, `strong`, `em`, `del`), `#write`, `.md-fences`, `.md-end-block`, классы, потомки (`a b`), прямые дети (`a > b`), группы через запятую, `:root`, `:hover` (только для ссылок), `:nth-child(odd|even)` для таблиц.
- Переменные `--name` и `var(--name, fallback)`; обязательные имена Typora: `--bg-color`, `--text-color`, `--primary-color`, `--select-text-bg-color`, `--side-bar-bg-color`, `--active-file-bg-color`, `--item-hover-bg-color`, `--control-text-color`.
- Свойства: `color`, `background`/`background-color`, `font-family`, `font-size`, `font-weight`, `font-style`, `line-height`, `text-align`, `text-decoration`, `margin*`, `padding*`, `border*` (включая `-left`, `-bottom`), `border-radius`, `max-width`, `width`, `letter-spacing`, `list-style-type`.
- Единицы: `px`, `em`, `rem`, `%`, `pt`, цвета `#rgb`, `#rrggbb`, `rgb()`, `rgba()`, `hsl()`, имена.
- `@font-face` с `src: url(локальныйфайл)` рядом с `.css` — `Font.createFont`; форматы `ttf`, `otf`; `woff` — по возможности.
- Каскад: специфичность → порядок → `!important`; слои: встроенные значения → тема → `*.user.css`.

Безопасно игнорировать (писать `CssDiagnostic`): `@media` (кроме `prefers-color-scheme`, если реализуем), анимации, `::before`/`::after`/счётчики, `calc()`, `flex`/`grid`, `position`, `transform`, `@import` по сети, внешние URL.

## 5. Фазы

Для каждой: **Цель → Модули/классы → Чек-лист → Приёмка (DoD)**.

### Фаза E0. Каркас
- Цель: модуль, регистрация, пустой `EditorCanvas`.
- Классы: `MarkdownEditorPlugin`, `MarkdownFileEditorProvider`, `MarkdownFileEditor`, `EditorCanvas`, `plugin.yml` (вклады: провайдер, действия переключения режима, настройки).
- Чек-лист: Gradle-модуль, `module-info`, i18n-ключи ru/en, открытие `.md` в существующей вкладке через провайдер с приоритетом выше `TextFileEditorProvider`; переключение Canvas/Source внутри той же вкладки, простой Source используется как откат до появления текстового ядра; настройка режима по умолчанию.
- DoD: `.md` открывается в canvas, закрытие/восстановление сессии работает, CI зелёный на 3 ОС.

### Фаза E1. Текстовое ядро
- Классы: `TextBuffer`(PieceTable), `DocumentModel`, `EditCommand`, `UndoManagerImpl`, `Position`, `Range`.
- Чек-лист: вставка, удаление, транзакции, группировка undo (по паузе/типу), свойства документа (EOL, кодировка, BOM), атомарное сохранение через существующий механизм `core`.
- DoD: property-тесты (случайные правки против `StringBuilder`), undo/redo возвращает ровно исходник, большие файлы (5 МБ) правятся за < 10 мс на символ.

### Фаза E2. Ввод и простой рендер
- Классы: `InputController`, `MouseController`, `KeyBindingResolver`, `ClipboardService`, `ImeSupport`, `CaretPainter`, `SelectionPainter`, `ViewportController`.
- Чек-лист: каретка и выделение мышью/клавиатурой, двойной/тройной клик, `Home/End/PageUp/PageDown`, перенос по словам, скролл с инерцией, IME, буфер обмена (текст), `Ctrl+Z/Y`, drag выделения; пока весь текст как исходник.
- DoD: по поведению не хуже `JTextArea` (чеклист ручной приёмки + тесты `hitTest`/`caretRect`), HiDPI корректен, Windows/Linux/macOS (учесть `Cmd` вместо `Ctrl`).

### Фаза E3. Парсер и дерево блоков
- Классы: `MarkdownParser` (на `flexmark-java` с расширениями), `AstIndex`, `BlockSpan`, `ParserExtensions` для: `FrontMatter`, `Wikilink`, `Embed`, `Callout`, `Footnote`, `Math`, `TaskList`, `Table`, `Strikethrough`.
- Чек-лист: источник позиций (spans) для каждого узла; инкрементальный `reparse` в пределах затронутых блоков; фон ≠ EDT.
- DoD: корпус тестовых `.md` (CommonMark spec examples, Obsidian-конструкции) → ожидаемые `BlockSpan`; правка одного символа не вызывает полного парса (метрика числа перепарсенных блоков).

### Фаза E4. Движок стилей
- Классы: `CssParser`, `StyleSheet`, `StyleResolver`, `ComputedStyle`, `TyporaCssLoader`, `FontRegistry`, `ThemeCatalog`.
- Чек-лист: подмножество из раздела 4, переменные, каскад, `*.user.css`, `CssDiagnostic`, встроенные темы Light/Dark в формате Typora-CSS (старые JSON-токены оболочки не трогать, но задать связку «тема оболочки → тема редактора»).
- DoD: все 6 встроенных и 3 дополнительные темы (список в `TYPORA_FEATURES.md`, раздел 9) загружаются без исключений; тесты на фиксированные значения `ComputedStyle` для `h1`, `blockquote`, `pre`, `table`; журнал диагностик непустой, но без фатальных.

### Фаза E5. Раскладка и WYSIWYG-текст
- Классы: `LayoutEngine`, `BlockBox`, `LineBox`, `InlineRun`, `LayoutCache`, `BlockRenderer`, `InlineRenderer`, `SourceBlockRenderer`, `EditorMode`.
- Чек-лист: заголовки, абзацы, цитаты, списки (в т.ч. вложенные), task list, `hr`, inline-стили, ссылки, inline-код, блок кода (простая подсветка), скрытие маркеров, переход блока в исходник по каретке, `Source Mode`, `max-width` и центрирование `#write`.
- DoD: на 6 эталонных темах выполняется трёхуровневая проверка рендеринга; `modelToView`/`viewToModel` согласованы; ввод в документе на 5000 строк — рисование кадра < 16 мс.

**Проверка рендеринга E5 (без требования пиксельного совпадения между ОС):**
1. **Метрики раскладки.** Все тесты используют встроенный свободно лицензированный шрифт через `Font.createFont` (Noto или JetBrains Mono после проверки лицензии), явный `FontRenderContext` с antialiasing и fractional metrics, фиксированные viewport/scale/DPI. Координаты сравниваются с допуском 0,5–1 px на всех ОС.
2. **Команды рисования.** Тонкий `DrawSink` имеет экранную реализацию поверх `Graphics2D` и тестовую, записывающую команды `fillRect`, `drawLine`, `drawText` и параметры стиля. Небольшие читаемые эталоны проверяют полосу blockquote, фон и скругление pre без растеризации.
3. **Golden-изображения.** Для каждой ОС используются `golden/windows/`, `golden/linux/`, `golden/macos/`; отсутствие файла — понятная ошибка с подсказкой `-Dgolden.update=true`. Используются `TYPE_INT_ARGB`, явные hints, серое текстовое сглаживание без LCD. Сравнение — с измеренным порогом по доле пикселей/каналов или блоками 8×8; порог устанавливается после замера разброса на трёх ОС. При падении сохраняются `actual.png`, `expected.png`, `diff.png`, CI публикует их как артефакты. Для каждой темы и ОС достаточно 3–5 изображений; полный набор из 9 тем выполняется на Linux headless, остальные ОС дополнительно проходят уровни 1–2. Retina проверяется явным тестом scale=2, не реальным экраном.

### Фаза E6. Режимы и действия
- Классы: `FocusMode`, `TypewriterMode`, `EditorContextMenuBuilder`, `EditorContext`, `EditorActionIds`.
- Чек-лист: F-002…F-005, F-049, F-066, F-067; контекстное меню ПКМ через `ActionGroup` с условной видимостью по контексту; меню Paragraph/Format/Insert; палитра команд; keymap-профиль «Typora».
- DoD: каждое действие вызывается из меню, горячей клавиши и палитры; тест `EditorContextMenuBuilder` проверяет состав меню по типу блока.

### Фаза E7. Виджеты
- Классы: `ImageWidget`, `MathWidget` (`JLaTeXMath`), `TableWidget`, `CodeBlockWidget`, `CalloutWidget`, `FrontMatterWidget`, `WidgetRegistry`, `ImageCache`.
- Чек-лист: картинки (относительные пути, ресайз, drag&drop, вставка из буфера), формулы inline и блочные, таблицы с редактированием структуры (Tab, добавить/удалить строки и столбцы, выравнивание), копирование/удаление ячеек; callouts; front matter как свёрнутая карточка с редактированием исходника; `BlockRendererExtension` и демо-плагин для `mermaid` (необязательный).
- DoD: фоновая загрузка не блокирует EDT (тест с искусственной задержкой), `TableWidget` изменяет только исходник таблицы и сохраняет остальной документ побайтно.

### Фаза E8. Obsidian и фишки Manuscriptium
- Классы: интеграция с `ObsidianLinkResolver`, `ObsidianLinkNavigator`, `ObsidianImagePreview` (из `core.editor`, переиспользовать), `TransclusionWidget`, `BookViewDocument`, `CrossReferenceIndex`, `CitationIndex`, `MarginNotesPane`.
- Чек-лист: F-120…F-123 сразу; затем F-M01 → F-M06 по приоритету.
- DoD: нет регрессии относительно текущего редактора (все тесты `Obsidian*Test` проходят на новом canvas); трансклюзия редактируется в месте и сохраняет изменения в исходный файл главы.

### Фаза E9. Экспорт и полировка
- Чек-лист: экспорт HTML/PDF из AST и стилей темы; `Exporter` как SPI (Pandoc — плагином); печать; производительность; доступность (a11y: `AccessibleContext` для canvas); журнал диагностик тем в UI. Сюда перенесены F-007 (автосохранение и история), F-008 (Zoom), полный F-060 (HTML→Markdown и копирование HTML), F-062 (поиск/замена) и подключение outline из F-100.
- DoD: PDF/HTML открываются, внешний вид соответствует теме в пределах «почти так же»; профайл на 50-страничном документе — без заметных задержек.

## 6. Правила работы с документом

- Любое изменение идёт только через `DocumentModel.apply`.
- Сохранение — атомарно (временный файл → `Files.move(ATOMIC_MOVE)`), с учётом symlink: сохранять в целевой файл, а не заменять ссылку.
- Неизвестный YAML в front matter сохраняется побайтно, порядок ключей не меняется.
- Переключение `SOURCE`/`WYSIWYG` не меняет текст.

## 7. Формат отчёта по каждой фазе

1. Список созданных/изменённых файлов (полные пути).
2. Результат команд: `./gradlew :plugins:markdown-editor:test`, общий `./gradlew build`.
3. Чек-лист фазы (отмечено/не отмечено, с причиной).
4. «Принятые решения» — 3–7 строк для переноса в следующий тред.
5. Известные ограничения.

## 8. Риски

- Согласованность `modelToView`/`viewToModel` со скрытыми маркерами — вынести в отдельный набор тестов в E5.
- Производительность раскладки — ввести метрики кадра с E2.
- Лицензии тем Typora: поставлять только свои/свободно лицензированные темы; пользовательские подгружать из каталога пользователя.
- Эталонные темы используют CSS вне подмножества — это ожидаемо; важно не падать и писать диагностику.
