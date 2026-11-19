# Manuscriptium

Java 21, Gradle/JPMS, Swing + FlatLaf. Реализованы расширяемая оболочка, внешние JAR-плагины и Project: Markdown-файлы, рекурсивный watcher, пересобираемые SQLite/Lucene-индексы, полнотекстовый поиск, ленивое физическое дерево и виртуальные YAML-деревья с DnD-клонированием ссылок. Из коробки JPMS-модуль `markdown` включает Canvas Editor, Flexmark AST Parser, девять Typora-подобных тем, JLaTeXMath Renderer, «Заметки» и «Свойства».

## Первый запуск 
```
gradle wrapper --gradle-version 8.14.4   # нужен Gradle >= 8.14.4 для плагина jlink 4.x
./gradlew build
./gradlew :app:run                       # окно с явно подключённым встроенным Markdown
./gradlew :app:devHome :app:jlink          # core, editor, markdown и их зависимости попадают в runtime image
app/build/image/bin/manuscriptium --smoke --home=app/build/dev-home
./gradlew :app:minimalSmoke             # независимый named-module запуск core + editor без markdown
```

При пустом состоянии центральная панель предлагает **New File**, **New Project**, **Open Project** и **Open File**. Эти кнопки
вызывают те же зарегистрированные действия, что и меню **File**. New File использует `CREATE_NEW` и не перезаписывает
существующий файл; New Project создаёт вложенные каталоги, но не заменяет обычный файл каталогом. Отдельный Markdown-файл
можно сразу редактировать и сохранять без предварительного открытия Project.

`app` собирает полный Manuscriptium из `core`, `editor`, `markdown` и их метаданных. Тестовый source set `minimalSmoke`
находится внутри того же Gradle-модуля `app`, но имеет собственный `module-info.java` и не включает ни основной output
`app`, ни `markdown`. Его JPMS dependency assertions подтверждают, что платформенные модули не требуют `ru.mcs.markdown`,
а единственный зарегистрированный editor provider — `editor.text`. Smoke открывает `plain.txt`, выполняет insert,
undo/redo и дожидается завершения Save. Задача `:app:check` зависит от `minimalSmoke`, поэтому минимальная компоновка
проверяется обычной сборкой; отдельный модуль `product-minimal` не создаётся.

Встроенные возможности продукта не являются внешними плагинами: `app` явно передаёт `MarkdownModule` в `CoreBootstrap`.
Модуль `markdown` не имеет `plugin.yml`, не создаёт отдельный `ModuleLayer`, не отображается в **Settings → Plugins** и не
отключается по отдельности. Внешние JAR по-прежнему устанавливаются в `plugins/` внутри каталога данных (`--home`),
имеют собственный `ModuleLayer` и независимо включаются/отключаются с перезапуском. Установка внешнего JAR не изменяет
файлы установленного приложения.

Restart повторяет module/classpath и JVM-аргументы текущего процесса. Новый процесс ждёт завершения прежнего PID до открытия
SQLite/Lucene и не наследует консоль Gradle/IDE: его stdout/stderr сохраняются в `<home>/logs/restart.log`.

## Модульная граница SDK

`spi` (`ru.mcs.spi`) содержит только платформенно-нейтральные контракты, идентификаторы, DTO, события и сервисы и не
требует `java.desktop`. Все AWT/Swing-сигнатуры находятся в `spi-swing` (`ru.mcs.spi.swing`): `Plugin`, actions,
file editors, rendering/style API, settings, status bar и tool windows. Модуль `spi-swing` транзитивно требует `spi`;
поэтому desktop-клиенты и плагины подключают `spi-swing`, а нейтральные API остаются доступны без обратной зависимости
`spi → spi-swing`. `SpiPurityTest` проверяет скомпилированные classfiles, а `SpiSwingModuleTest` — публичные контракты
и JPMS exports.

## Декларативные метаданные

`metadata` (`ru.mcs.metadata`) — JDK-only модуль версионированных описаний приложения без Swing/AWT и прикладной логики.
Строгий JSON-parser и typed model поддерживают `formatVersion=1`, actions, menus, toolbars, status bar, settings pages,
keymap и panels. Валидация проверяет уникальность и перекрёстные ссылки идентификаторов, позиции и anchors, типы полей,
targets и capabilities. Правила `common`/`desktop`/`android`/`ios` и required/excluded capabilities выбирают применимость
описания, но не создают мобильные клиенты.

`editor`, `core` и `markdown` поставляют собственные JSON в `META-INF/manuscriptium/` через `MetadataProvider`. Провайдеры
объявлены одновременно в JPMS (`provides`/`uses`) и `META-INF/services`, поэтому каталог одинаково загружается в named
runtime image и classpath-тестах. Реализации команд остаются в Java; описания определяют тексты, группы, родителей,
порядок, shortcuts и формы настроек.

`DesktopMetadata` в `core` — единственный desktop renderer этого среза. Он объединяет вклады модулей и постепенно связывает
menus/toolbars повторными проходами до неподвижной точки: entry с `before`/`after` ждёт появления anchor и не деградирует
в `last`. Android/iOS-рендереры не создаются.

## Универсальный Project и языковые вклады

`ProjectAccess`, `ViewDefinition`/`ViewEntry`, `IndexedDocument`, `IndexContributorRegistry`, `ProjectFileFactory` и
`BranchStatisticsService` являются платформенными SPI-контрактами. Общий индекс хранит title, metadata, content,
searchContent и произвольные attributes; SQLite schema имеет `user_version=2` и при смене версии пересобирается из исходных
файлов. Binary-файлы индексируются по имени/пути с пустым текстом, `.manuscriptium` и `.git` не сканируются.

Модуль `markdown` регистрирует `MarkdownIndexContributor`, `ProjectFileFactory`, `BookService` и `BranchStatisticsService`.
MarkdownParser, EditorStyleService, Obsidian-навигация и fallback editor также находятся в `markdown`; production-зависимости
`markdown → core` и `core → markdown` нет. `PlatformLanguagePurityTest` запрещает Markdown/Obsidian/Front Matter идентификаторы
в production Java и resources платформы, а `NoForbiddenModuleDependenciesTest` проверяет направления и в JPMS, и в Gradle.

## Универсальный редактор

`editor` (`ru.mcs.editor`) содержит языконезависимые DocumentModel/Piece Table, undo/redo и save-point, Canvas, layout,
caret/selection, viewport, IME, clipboard и keyboard/mouse input. Он зависит от `spi-swing` и `metadata`, но не знает о `core`,
Markdown, Obsidian или Front Matter; это проверяется `EditorPurityTest` по production classfiles и JPMS descriptor. Собственное
`editor.json` описывает Undo/Redo и clipboard shortcuts, а movement/navigation остаются в Java.

Обычные файлы до 5 МиБ открывает `editor.text` через `ServiceLoader<FileEditorProvider>`: `core` объявляет только `uses`,
а `app` включает модуль в runtime image. Загрузка и Save выполняются вне EDT; strict UTF-8, BOM, LF/CRLF/CR, атомарная
запись и symlink-target semantics совпадают с Markdown editor. `markdown.fallback` сохраняет прежний редактор с
Obsidian-навигацией для компоновки продукта без модуля `markdown`. Markdown расширяет базовый Canvas через
`MarkdownCanvas extends EditorCanvas`; обратной зависимости `editor → markdown` нет. Native image DnD по-прежнему включён
в обычном desktop UI, но `DropTarget` не создаётся при `GraphicsEnvironment.isHeadless()`, поэтому текстовая модель и Save
можно проверять в headless `minimalSmoke` без изменения обычного XDND-поведения.

## Markdown Canvas Editor — E0–E8

`.md` открывается в той же вкладке редактора через встроенный JPMS-модуль `markdown` с приоритетом выше встроенного
текстового провайдера. Кнопки **Canvas** и **Source** внутри вкладки переключают представление без создания второй вкладки
или окна предпросмотра; действие также доступно в View, toolbar и popup вкладки. Режим для новых вкладок настраивается в
**Settings → Markdown Editor** и сохраняется между запусками.

Собственный `EditorCanvas` и общий session store дополнены текстовым ядром E1: `TextBuffer` на Piece Table, команды
insert/delete/replace/composite, вложенные транзакции и undo/redo с группировкой соседних однотипных правок по паузе 500 мс.
Save-point учитывает undo/redo и правки во время фонового сохранения. Модель использует UTF-16 offsets и внутренние LF;
при загрузке строго проверяются UTF-8, сохраняются BOM и однородные LF/CRLF/CR. Смешанные EOL отклоняются при сохранении,
чтобы не переформатировать весь файл без явной команды пользователя.

`AtomicFileStore` из SPI реализован в `core` для плагинов; универсальный `editor` использует собственную JDK-only реализацию
того же контракта записи без зависимости на `core`: temp-файл создаётся в каталоге целевого файла, применяется `ATOMIC_MOVE`,
а symlink сохраняется и ведёт запись в его target. Чтение и запись выполняются вне EDT; Save, Save All, закрытие вкладки, выход,
перезапуск и смена Project ждут завершения фонового сохранения.

В E2 оба режима используют редактируемый собственный lightweight canvas и общую `DocumentModel`; основной Markdown и
обычный текст не используют `JTextArea` (он остаётся только в Markdown fallback и внутри transclusion editor). Поддержаны клавиатурный ввод, каретка и selection, drag/double/triple click, word wrap, Home/End/PageUp/PageDown,
текстовый clipboard, undo/redo с системным menu shortcut (`Cmd` на macOS), IME и скролл с инерцией. Построчная раскладка
изменяет только затронутые физические строки, а рисование ограничено viewport. Source сохраняет эту исходную раскладку;
Canvas начиная с E5 использует отдельный WYSIWYG layout над той же моделью документа.

В E3 модуль `markdown` предоставляет Flexmark AST через JDK-only SPI. `BlockSpan` сохраняет нулевые
полуоткрытые UTF-16 offsets и depth каждого узла; поддержаны Front Matter, wikilinks/embeds, callouts, footnotes, inline/block
math, task lists, tables и strikethrough. `AstIndex` выполняет поиск блока по offset. Обычная правка перепарсивает только
затронутые top-level блоки и переиспользует остальные; результаты вычисляются в общем daemon executor и применяются на EDT
только при совпадении `DocumentModel.version()`. В E5 актуальный AST управляет блочной раскладкой Canvas; устаревший AST после
ввода не применяется к новой версии документа. Следующие фазы и критерии приёмки определены в `EDITOR_CANVAS_PLAN.md`, каталог
поведения — в `TYPORA_FEATURES.md`.

### Темы редактора E4

Модуль `markdown` загружает девять Typora-подобных CSS-тем: `github`, `newsprint`, `night`, `pixyll`,
`whitey`, `vue`, `lapis`, `onedark` и `notion`. В автоматическом режиме светлая тема оболочки выбирает `github`, тёмная — `night`;
явный выбор темы редактора через `EditorStyleService` отключает это автоматическое следование. JSON-токены и переключение
FlatLaf-тем оболочки не изменены.

Модуль предоставляет `EditorStyleService` из `ru.mcs.markdown.api` с типизированным AWT-ориентированным `ComputedStyle` из `spi-swing`. CSS-движок поддерживает каскад и `!important`,
type/id/class/`:root`/`:hover`/`:nth-child(odd|even)`, descendant- и child-комбинаторы, группы селекторов, переменные `--name` и
`var()`, размеры, цвета, отступы, границы, шрифты и локальные `@font-face`. Неподдерживаемые свойства и конструкции попадают в журнал
как `CssDiagnostic` и не прерывают загрузку остальной темы. CSS и TTF/OTF-шрифты загружаются в daemon executor вне EDT; внешние
font URL не загружаются, а локальные файлы разрешаются только внутри каталога темы.

Пользовательские переопределения необязательны и размещаются в `<home>/editor-themes/`:

- `base.user.css` — для всех тем;
- `{theme}.user.css`, например `github.user.css` — только для выбранной темы.

Итоговый порядок слоёв: `DEFAULT → THEME → BASE_USER → THEME_USER`. Выбранный режим (`auto` или id темы) сохраняется в application
state. Отдельного UI выбора editor theme и журнала диагностик пока нет; переключение Light/Dark оболочки обновляет Canvas через
change listeners `EditorStyleService`.

### WYSIWYG-раскладка E5

Canvas отображает заголовки, абзацы, цитаты, вложенные и нумерованные списки, task list, `hr`, inline-стили, ссылки, inline-код
и блоки кода с простой подсветкой. Markdown-маркеры скрываются в неактивных блоках; блок под кареткой показывается как исходник.
Source Mode всегда содержит полный исходный Markdown. Оба режима редактируют общую `DocumentModel`, поэтому ввод, selection,
undo/redo и сохранение не разделяются между представлениями.

`#write` ограничивает ширину и центрирует текст; раскладка кэшируется по версии документа, ширине и активному блоку. Каретка,
hit test и selection используют отображение видимых UTF-16 boundaries в исходные offsets, включая Unicode и перенос строк.
Векторный checkbox переключает один символ `[ ]`/`[x]`; Ctrl/Cmd+Click открывает только HTTP/HTTPS-ссылки.

Проверка рендеринга включает метрики со встроенным Liberation Sans, `DrawSink` command tests и девять Linux goldens при scale=2.
При расхождении создаются `actual.png`, `expected.png` и `diff.png` в `markdown/build/reports/golden-diff/`.
Для пересоздания Linux-эталонов из корня проекта:

```
./gradlew :markdown:test --tests ru.mcs.markdown.style.MarkdownGoldenTest \
  -Dmanuscriptium.golden.update=markdown/src/test/resources/golden/linux
```

### Режимы и действия E6

**F8** включает Focus Mode, приглушая неактивные блоки без потери контраста selection и каретки. **F9** включает Typewriter
Mode и удерживает строку каретки по вертикальному центру viewport, включая первую и последнюю строки документа. Оба режима
доступны также через **View** и палитру команд **Ctrl+Shift+A**; текст и внутренняя Markdown-раскладка не изменяются.

Меню **Paragraph**, **Format** и **Insert** содержат заголовки, inline-форматирование, списки, ссылки, код, таблицы, math,
`hr` и изображения. ПКМ использует те же действия и добавляет блок-специфичную команду по контексту каретки, включая **Link**.
Keymap-профиль Typora поддерживает, среди прочего, **Ctrl+B/I**, **Ctrl+Shift+U**, **Ctrl+K**, **Ctrl+0…6**,
**Ctrl+Shift+Q**, **Ctrl+T**; на macOS вместо Ctrl используется Cmd. Полный список привязок находится в `TyporaKeymap`,
а все команды доступны в палитре.

IntelliJ-подобные команды работают в Canvas и Source: **Ctrl+U** переключает регистр selection или текущего слова,
**Ctrl+D** дублирует строку и переводит каретку на копию, **Ctrl+Y** удаляет строку, **Ctrl+W** последовательно расширяет
selection от слова до соседних слов и всей строки. Redo выполняется через **Ctrl+Shift+Z**. Enter в Canvas сразу создаёт
видимую пустую строку и перемещает на неё каретку, не дожидаясь следующего символа или фонового AST. После актуального AST
каретка остаётся на этой physical line, включая пустые строки в конце документа и между параграфами.

**Ctrl+Shift+P** переключает Auto Pair для скобок, кавычек, backticks, `**` и `__` в Canvas и Source. **Ctrl+Shift+W** показывает
слова, Unicode-символы, строки и оценку времени чтения для selection или всего документа. **Ctrl+Shift+D** удаляет текущий
блок. Formatting, Undo/Redo, selection и сохранение работают над общей `DocumentModel` независимо от активного представления.

### Виджеты и изображения E7

`WidgetRegistry` создаёт lightweight-виджеты для неактивных блоков: изображения, inline/block math, таблицы, callouts,
front matter и код. Виджет занимает обычную высоту в `LayoutResult` и рисуется через `DrawSink`, не становясь тяжёлым Swing
child component. Активный блок остаётся исходником, поэтому каретка, Enter, selection и IME сохраняют поведение E6.

Блочные Markdown-изображения и Obsidian `![[image]]` загружаются вне EDT через `ImageCache` с LRU-лимитом фактических
RGBA-байтов. Разрешены только относительные пути, которые после `normalize()` остаются внутри каталога заметки. Resize за
угловой handle применяет `Replace` только к исходнику изображения при неизменной версии документа и сериализуется как
`![alt|320](assets/image.png)` или `![[assets/image.png|320]]`. Смешанные inline-изображения внутри текстового абзаца пока
остаются текстовым отображением.

Drag&drop и вставка file list копируют файлы в `assets`; изображение из буфера сохраняется как PNG. Коллизии разрешаются без
overwrite: `photo.png`, `photo-1.png` и далее. В Markdown вставляется путь относительно каталога заметки, например
`![](assets/clipboard-image.png)`. Для Canvas/Source активен ровно один native drop-target: скрытая карточка `CardLayout` его
отключает, поскольку оба lightweight-редактора делят одно heavyweight окно. Это сохраняет настоящий XDND на Linux и предотвращает
снятие `XdndAware` с окна.

Модуль `markdown` включает JLaTeXMath как automatic module в runtime image и предоставляет JDK-only
`MathRenderingService` без JLaTeXMath-типов в публичных сигнатурах. Inline math является неделимым `InlineContent.Atom` с source offsets, hit test, caret mapping и selection;
LaTeX fallback с пробелами не разбивает формулу на повторные изображения. Поддержаны `$…$`, однострочный `$$…$$` и многострочный
display block. Рендеринг выполняется вне EDT.

Таблица поддерживает Tab-навигацию, добавление/удаление строк и столбцов, выравнивание, копирование и очистку ячейки. Девять
команд добавлены в **Format → Table**, палитру и Typora keymap. Каждая структурная операция применяет один `Replace` к фактическому
source-диапазону таблицы и сохраняет prefix/suffix документа неизменными; форматирование separator-строки самой таблицы может
нормализоваться. Callouts и front matter отображаются карточками; исходник front matter доступен при активации блока.

JDK-only `BlockRendererExtension` принимает `BlockRenderRequest` и асинхронно возвращает `RenderedBlock`, не раскрывая Flexmark
или JLaTeXMath в SPI. Необязательный mermaid demo-плагин не создавался. Delayed decoder/renderer-тесты подтверждают отзывчивость
EDT, а packaged UI-приёмка покрывает resize, таблицы, clipboard file/image, настоящий межпроцессный XDND-drop и Enter до/после AST.

### Obsidian, трансклюзии и рукописи E8

Canvas отображает `[[Note|Alias]]` без Markdown-скобок вне активного блока; Ctrl+Click (Cmd+Click на macOS) использует общий
`ObsidianLinkNavigator` и Project-safe resolver из `core` через JDK-only SPI. Комментарии `%%…%%` скрываются вне каретки. Вкладка
**Notes** рядом с **Canvas**/**Source** открывает правое поле ревизий: **Add Note** и **Edit** применяют точные `Insert`/`Replace`
к исходным UTF-16 offsets и участвуют в общем undo/redo.

`![[note]]` встраивает всю заметку, а `![[note#Heading]]` — заголовок и содержимое до следующего заголовка того же или более
высокого уровня. Во встроенной рамке доступны **Open original** и **Save**. Сохранение повторно читает актуальный исходник,
заменяет только выбранную секцию и атомарно пишет исходный файл; родительский Markdown не изменяется, а более свежие правки
других embed-сессий не затираются устаревшими `prefix`/`suffix`.

Popup корня или узла виртуального view содержит **Open as Book**. Книга открывается как виртуальная вкладка со сквозным списком
видимых глав; скрытые записи и поддеревья исключаются. **Save Chapters** записывает только изменённые исходные файлы, а
**Export Node…** объединяет текущий, в том числе ещё не сохранённый, текст и создаёт результат через `CREATE_NEW`, не перезаписывая
существующий файл или symlink. Виртуальная книга не восстанавливается после перезапуска как несуществующий Markdown.

Явные якоря `{#sec:name}`, `{#fig:name}` и `{#tbl:name}` получают вычисляемые номера, а ссылки `#sec:name`, `#fig:name` и
`#tbl:name` отображаются как Section/Figure/Table с номером. Подписи используют alt изображения; исходный Markdown не изменяется.
Якоря в front matter и fenced code игнорируются, duplicate id не пропускает номер следующего уникального якоря.

Front matter `bibliography: refs.bib` и необязательный `csl: style.csl` включают citations `[@key]`, группы `[@first; @second]`
и locators `[@key, p. 12]`. Поддержаны вложенные BibTeX-braces, quoted values, author/editor, year/date, title и author-date
`citation/layout` из CSL. Ввод незавершённого `[@prefix` показывает completion ключей; Canvas вычисляет список литературы только
для фактически цитируемых известных ключей. Неизвестный citation остаётся исходным текстом. `.bib`/`.csl` читаются вне EDT через
тот же Project-safe resolver. Это не полный CSL-процессор: поддерживается описанное author-date-подмножество.

**Branch Statistics…** в popup физического и виртуального дерева считает число заметок, Unicode-слова, UTF-16-знаки и время чтения
(200 слов/минуту, округление вверх). Диск читается вне EDT; физическая ветка использует индексированные `ProjectNote`, виртуальная —
видимую проекцию книги. Устаревший результат после смены Project игнорируется.

## Встроенные панели заметок и свойств

**Заметки** показывает открытые Markdown-вкладки. Двойной щелчок, Enter или кнопка открытия переключают активную
заметку; для выбранного файла в фоне вычисляются слова, UTF-16-символы и строки исходного Markdown. **Copy Wiki Link**
(в панели, Tools, toolbar и popup вкладки) копирует короткую Obsidian-ссылку вида `[[Note]]` для активной заметки.

**Свойства** отображает front matter активной `.md`: `title`, список `tags` через запятую и `draft`. Остальные поля
показаны отдельно только для чтения и не сериализуются заново. **Refresh** перечитывает файл с диска, **Apply** сначала
сохраняет открытые редакторы, затем изменяет только управляемые поля и переоткрывает активную заметку, чтобы source-
редактор и виджеты не расходились. Поддержаны inline- и dash-списки тегов, quoted scalars, BOM и CRLF; тело документа
и неизвестные поля сохраняются. Front matter у новой заметки создаётся только по Apply. Дубликаты управляемых ключей,
незакрытый блок и неподдерживаемые значения отклоняются. Запись выполняется через temp-файл и атомарную замену;
symlink в файле или родительском каталоге запрещён. Обе панели используют только SDK 0.10; «Свойства» не регистрирует собственный FileEditorProvider.

## Project

Выберите `File → Open Project…` и укажите существующий или пустой каталог. Импорт не нужен: все `.md` внутри каталога
остаются источником истины и открываются из панели Project. Каталоги `.git` и `.manuscriptium` не индексируются.

Новая заметка создаётся через `File → New Markdown Note…`, одноимённую кнопку на toolbar или popup на вкладке **Files**.
В диалоге указывается относительный путь, например `inbox/idea.md`; недостающие каталоги создаются автоматически.
Существующий файл не перезаписывается, пути вне Project и прохождение через symlink запрещены. Созданная заметка сразу
открывается в редакторе.

Служебные данные создаются в `<project>/.manuscriptium/`:

- `index.db` — пересобираемый SQLite-кэш. При остановленном приложении его можно удалить вместе с `index.db-wal`
  и `index.db-shm`; при следующем открытии индекс будет создан заново из Markdown;
- `lucene/` — пересобираемый полнотекстовый индекс. Его также можно удалить при остановленном приложении;
  при следующем открытии Project он будет создан заново из тех же Markdown-файлов;
- `state/` — состояние этого Project, включая открытые вкладки;
- `views/` — виртуальные деревья `.yml`, содержащие упорядоченные ссылки на Markdown, а не копии заметок.

Последний Project открывается автоматически. Внешние создание, изменение и удаление `.md` и `.manuscriptium/views/*.yml`
подхватываются через `WatchService`. Ошибка одного view отображается на отдельной вкладке и не мешает остальным views.

### Полнотекстовый поиск

Панель **Search** открывается кнопкой поиска на toolbar, через **Tools → Search in Project** или **View → Show Search**.
Запрос состоит из литеральных слов без специального Lucene-синтаксиса: регистр не важен, все слова должны встретиться
в одной заметке. Область поиска — все поля, заголовок, содержимое, metadata, путь или attributes. Для Markdown тело и
Front Matter индексируются отдельно, tags/draft становятся generic attributes. Дополнительно можно ограничить результаты
относительным подкаталогом (например, `notes/java`)
и состоянием `draft`; фильтры работают и без текстового запроса.

Поиск выполняется в фоновой очереди Project и не блокирует редактор. Показываются до 100 наиболее релевантных результатов
с фрагментом совпадения; двойной щелчок или Enter открывает заметку в существующем редакторе. При смене Project или новом
запросе поздно завершившийся старый поиск не изменяет панель. Создание, редактирование и удаление Markdown подхватываются
watcher и обновляют Lucene инкрементально. Невалидный YAML не мешает искать текст, но structured-метаданные такой заметки
используют безопасные значения по умолчанию. Каталоги `.manuscriptium` и `.git` в поиск не попадают.

### Виртуальные деревья

Пример `.manuscriptium/views/classification.yml`:

```yaml
name: Классификация
entries:
  - node: Научная литература
    override-title: Наука
    visibility: true
    tags: [science]
    children:
      - node: Java
        children:
          - file: notes/java.md
            override-title: Java 21
            visibility: true
            tags: [programming]
```

`node` и `file` взаимоисключающи; `children` допустимы только у `node`. Путь в `file` относительный, с `/`, без `..`.
`override-title` меняет только отображаемое имя, `visibility: false` скрывает запись вместе с потомками, `tags` — метаданные ссылки.
Отсутствующий файл не удаляется из YAML, а помечается в дереве.

Во вкладке view обычный DnD переносит ссылку файла в другой узел, Ctrl+DnD клонирует её. Физический `.md` не перемещается.
Те же операции доступны без DnD: одиночный левый или правый щелчок по ссылке открывает popup с командами **Open**,
**Clone Link to…** и **Move Link to…**. Диалог предлагает только допустимые видимые цели, исключая текущего родителя и
дубликаты. Двойной щелчок по-прежнему открывает файл в редакторе.
Дубликат той же ссылки непосредственно в целевом узле отклоняется. Файл с двумя и более видимыми ссылками получает overlay клона.

Команды **Clone Link to…** и **Move Link to…** доступны также в popup `.md` на физической вкладке **Files**. Clone добавляет
ссылку в любой существующий view или создаёт новый view — ручная подготовка YAML не нужна. Move при необходимости предлагает
выбрать конкретную исходную ссылку и может перенести её внутри view, между views или в новый view. Целевой view открывается
после операции; физический Markdown не копируется и не перемещается. Имя нового YAML получает уникальный безопасный id,
существующий файл не перезаписывается. Сама вкладка **Files** раскрывает каталоги лениво и не участвует в DnD.

### Obsidian-разметка

Fallback source-редактор `markdown.fallback` подсвечивает `[[wikilinks]]`, `![[embed]]` и callouts вида `> [!note]`, `> [!warning]`.
Он используется в компоновке без модуля `markdown`. С E8 Canvas также поддерживает wikilinks, embeds, callouts,
комментарии и навигацию через SPI-сервисы без production-зависимости `markdown` от `core`.
Поддержаны alias (`[[Note|alias]]`), заголовки (`[[Note#Heading]]`, `[[#Local heading]]`) и block-id
(`[[Note#^block-id]]`). Ctrl+Click (Cmd+Click на macOS) открывает заметку и выбирает адресованный заголовок или блок.
Ссылки разрешаются относительно текущей заметки и корня Project; имя без каталога находит наиболее короткий совпадающий путь,
регистр имени не важен. Неразрешённая ссылка только выводит сообщение в статус-баре и не создаёт файл автоматически.

`![[note]]` открывает связанную заметку. Для `![[image.png]]` поддерживается предпросмотр PNG, JPEG, GIF, BMP и SVG
с масштабированием под окно; остальные вложения открываются ассоциированным приложением ОС. Путь не может выйти за Project,
а symlink в целевом файле или родительском каталоге не используется для перехода. Разрешение ссылок и декодирование изображений выполняются
вне EDT. Callouts получают цветовую подсветку по типу; маркеры `+`/`-` сохраняются, а сворачивание блоков пока не реализовано.
Редактируемые WYSIWYG-трансклюзии доступны в Canvas с E8. Front matter, inline code и fenced code не создают
ложных ссылок или callouts. Сам Markdown при навигации и предпросмотре не изменяется.

Язык выбирается через `File → Settings… → General`. После подтверждения изменения приложение предлагает перезапуск;
выбранный язык сохраняется в `<home>/state/core.settings.yml` и применяется к новому процессу.

## Разработка плагинов

- [Руководство по Plugin SDK 0.10](docs/plugin-sdk.md).
- [Standalone-шаблоны двух JAR-плагинов](plugin-template/): универсальное расширение через `spi-swing` и расширение
  формата через `ru.mcs.markdown.api`.
- Манифесты используют `sdk-version: "0.10"`; старые JAR линии `0.9` отклоняются и требуют пересборки.
- `depends-on: [markdown]` связывает внешнее расширение с фактически запущенным built-in модулем. Дополнительный
  plugin layer не создаётся: модуль и зарегистрированный `MarkdownParser` уже доступны из boot layer.
- Встроенные реализации Notes и Properties находятся в `markdown`; они используют тот же `PluginContext`, но не являются
  устанавливаемыми JAR-плагинами. Публичный API формата ограничен пакетом `ru.mcs.markdown.api`.
- Java-плагин имеет полный доступ к JVM; песочницы нет. Установка требует явного подтверждения предупреждения.
  Обновление проходит staging/pending и применяется при следующем запуске с rollback прежней установки при ошибке.
