# Manuscriptum — сводный аудит реализации

Дата снимка: 2026-11-16. Состояние кода: после принятия E8.

Этот документ объединяет требования и статусы из `plan.md`, `TYPORA_FEATURES.md`, `EDITOR_CANVAS_PLAN.md` и `REQUIREMENTS.md` и сопоставляет их с фактическими модулями, production-кодом и тестами. Это инвентаризация текущего состояния, а не новый план и не замена исходным документам.

## 1. Как читать статусы

- **Реализовано** — заявленное поведение присутствует в коде; возможные узкие ограничения указаны отдельно.
- **Частично** — работающая часть есть, но пункт требования шире реализованного поведения.
- **Не реализовано** — production-реализации соответствующей функции нет.
- **Перенесено** — не реализовано сейчас и явно отнесено к E9, Фазе 5, Фазе 6 или отдельному позднему этапу.
- **Не подтверждено** — код или конфигурация есть, но численный либо платформенный критерий нельзя считать доказанным текущими материалами.

Пути в таблицах относительные к корню репозитория. Используются сокращения:

- `editor/` — `plugins/markdown-editor/src/main/java/ru/mcs/plugin/mdeditor/`;
- `core/` — `core/src/main/java/ru/mcs/core/`;
- `spi/` — `spi/src/main/java/ru/mcs/spi/`.

Статус отражает функциональную полноту относительно формулировки требования, а не только наличие одноимённого класса.

## 2. Общий итог

1. Расширяемая Swing-оболочка, JPMS-плагины, локальная установка JAR, Vault, SQLite/Lucene, физические и виртуальные деревья и Canvas-редактор E0–E8 реализованы.
2. E9 не начинался. Поэтому отсутствуют экспорт HTML/PDF, печать, Zoom, поиск/замена в редакторе, полный HTML-clipboard, outline, accessibility Canvas и UI журнала CSS-диагностик.
3. Фаза 5 публикации и Фаза 6 не начинались. Hugo, PublishingProfile, JGit, синхронизация, скрипты, граф, Kanban, AI и мобильный контур отсутствуют.
4. Завершение E0–E8 не равно выполнению всех пунктов `TYPORA_FEATURES.md`. Среди P0 остаются полный F-060, F-062 и outline из F-100; кроме того, есть частичные реализации ссылок, списков, кода, таблиц, изображений и автонумерации.
5. `REQUIREMENTS.md` описывает существенно более широкий исходный MVP, чем текущий маршрут `plan.md`. Шаблоны/QuickAdd, несколько независимых синхронизируемых репозиториев, Git, публикация и мобильная быстрая запись пока не образуют заявленный там сквозной MVP.
6. Последняя зафиксированная приёмка E8: `clean test :app:devHome :app:jlink`, 502 теста без failures/errors/skipped, девять Linux golden-тем, bundled/external smoke и packaged UI-сценарии. Пользователь подтвердил сборку, запуск и коммит. В рамках этого документального аудита полный build повторно не запускался; спорные статусы проверялись по исходникам и тестовой структуре.

## 3. Статус по `plan.md` и `EDITOR_CANVAS_PLAN.md`

| Этап | Статус | Фактический результат и оставшиеся ограничения |
|---|---|---|
| Фаза 0. Каркас и риски | Реализовано | Java 21, multi-module Gradle, JPMS, jlink, `ModuleLayer`/`ServiceLoader`, внешний плагин и CI-матрица Ubuntu/Windows/macOS. Evidence: `settings.gradle`, `build.gradle`, `app/build.gradle`, `.github/workflows/build.yml`. |
| Фаза 1. Оболочка | Реализовано | Actions/groups/constraints, tool windows и docking, вкладки/закрепление/сессия, status bar, MessageBus, notifications, services/state и ленивые вклады. Evidence: `core/action/`, `core/toolwindow/`, `core/editor/`, `core/service/`, `core/state/`. |
| Фаза 2. Настройки, темы, i18n, палитра | Реализовано | Settings OK/Apply/Reset, FlatLaf Light/Dark, JSON-токены, системная тема, hot reload, SVG/Lucide, ru/en, macOS-интеграция, Command Palette, установка и включение/отключение плагинов. Реальная приёмка unified toolbar на физическом Mac отдельно не зафиксирована. |
| Фаза 3. Полный локальный цикл плагинов | Реализовано | Граф зависимостей, SDK compatibility, ошибки в UI, bundled/external источники, staging/rollback, pending update, SDK docs и standalone template. Сетевой каталог намеренно не входит. |
| Фаза 4. Vault и иерархия | Реализовано в объёме этапа | Markdown source of truth, watcher, SQLite/Lucene, поиск, физическое дерево, YAML views, clone/move, lazy tree, создание заметок, Notes/Properties и Obsidian source support. Это не закрывает более широкие R3, R5, R10–R16 и R24–R36 из `REQUIREMENTS.md`. |
| E0. Каркас редактора | Реализовано | Отдельный bundled plugin, один Markdown-tab с Canvas/Source, provider priority, настройка режима и ru/en. |
| E1. Текстовое ядро | Реализовано | Piece Table, команды, composite/вложенные транзакции, undo/redo, save-point, UTF-16, BOM/EOL, строгий UTF-8, атомарный и symlink-safe Save. |
| E2. Ввод и простой рендер | Реализовано | Два собственных lightweight Canvas над общей моделью, ввод, selection, mouse/keyboard, word wrap, clipboard, IME, инерционный scroll и инкрементальная line layout. |
| E3. Parser/AST | Реализовано | Отдельный Flexmark plugin, JDK-only SPI, точные spans, incremental dirty-window, background coordinator, corpus/randomized tests. |
| E4. CSS styles | Реализовано в согласованном subset | Отдельный plugin, каскад, переменные, селекторы, user CSS, локальные шрифты, девять тем и diagnostics. UI выбора editor theme и UI diagnostics отсутствуют; последний явно относится к E9. |
| E5. WYSIWYG layout | Реализовано в объёме этапа | Блочная/inline раскладка, активный source block, source mapping, styles, links, code, tasks, metrics/DrawSink/goldens и viewport performance test. Не все inline-конструкции каталога покрыты. |
| E6. Режимы и действия | Реализовано | Focus, Typewriter, Auto Pair, общий ActionManager, меню/ПКМ/палитра/Typora keymap, Word Count и дополнительные IntelliJ-подобные команды. |
| E7. Widgets | Реализовано с оговоркой | Images, math, tables, callouts, front matter, extension renderer и безопасный import. Смешанные inline-изображения остаются текстовыми; mermaid demo не создавался и был необязателен. |
| E8. Obsidian и рукописи | Реализовано в объёме этапа | F-120…F-123 и F-M01…F-M06, включая fresh-merge transclusion Save, virtual Book, citations subset и Branch Statistics. Полный CSL не заявлялся и не реализован. |
| E9. Экспорт и полировка | Перенесено / не начато | Нет HTML/PDF export, Exporter SPI, печати, Zoom, autosave/history, полного clipboard, Find/Replace, outline, a11y и UI diagnostics. |
| Фаза 5. Публикация | Перенесено / не начато | Нет `StaticSiteExporter`, `PublishingProfile`, Hugo YAML conversion, draft gate, broken-link validation, JGit и multilingual publishing. |
| Фаза 6. Расширения | Перенесено / не начато | Нет `McsFacade`, AI, JCEF plugin, graph, Kanban, SyncProvider/E2EE и runtime Java compilation. |

### 3.1. Отдельные инфраструктурные расхождения

- В `plan.md` перечислены jpackage-инсталляторы MSI/DEB/DMG, но в `app/build.gradle` настроен jlink image; задач создания установщиков нет.
- Пункт «одно окно — один Vault» выполнен для текущего однооконного приложения. Допустимость нескольких окон зафиксирована как архитектурный принцип, но UI создания нескольких независимых окон отсутствует.
- Раздел структуры репозитория в `plan.md` всё ещё показывает старые `demo-*`; фактические модули перечислены в `settings.gradle`.
- В `plan.md` повторяются блоки об Obsidian и Notes/Properties, а пункт о демо-плагинах продублирован.
- Открытый вопрос «RSyntaxTextArea vs JTextPane» устарел: выбран и реализован собственный Canvas.
- Формулировка «без UI-тестов Swing» устарела: существуют headless-тесты Swing-компонентов и packaged UI-приёмка, хотя TestFX не используется.
- MIT выбран в плане, но корневой файл `LICENSE` в снимке не найден.
- `.github/workflows/build.yml` проверяет точную строку bundled smoke без `ru.mcs.plugin.math`, хотя `app/build.gradle` включает `math-renderer`, а `markdown-editor` от него зависит. Это расхождение CI-конфигурации следует проверить при следующем этапе; локальная E8-приёмка не доказывает зелёный текущий remote workflow.

## 4. Каталог `TYPORA_FEATURES.md`

### 4.1. Базовая модель

| ID | Приоритет | Статус | Что есть и чего не хватает |
|---|---:|---|---|
| F-001 | P0 | Реализовано | Единый Canvas, активный top-level блок показывает исходник, остальные рендерятся. Evidence: `editor/layout/MarkdownLayoutEngine.java`. |
| F-002 | P0 | Реализовано с расхождением UI | Source Mode, `Ctrl+/`, общая модель и переключатель есть. Кнопки расположены на панели внутри вкладки, а не в общем status bar. Evidence: `MarkdownFileEditor.java`, `TyporaKeymap.java`. |
| F-003 | P1 | Реализовано | Focus Mode приглушает неактивные блоки, сохраняя каретку/selection. |
| F-004 | P1 | Реализовано | Typewriter центрирует строку через виртуальные viewport-отступы. |
| F-005 | P2 | Реализовано | Используется theme variable `--focus-mode-alpha`, по умолчанию 0,35. |
| F-006 | P2 | Не реализовано | Нет Strict Mode и соответствующего parser flag. |
| F-007 | P1 | Перенесено в E9 | Есть явный/фоновый Save и защита несохранённых редакторов, но нет периодического сохранения Markdown и version history. Timer в `CoreBootstrap` сохраняет только application state. |
| F-008 | P1 | Перенесено в E9 | Нет Zoom всего Canvas с перерасчётом layout. |
| F-009 | P0 | Реализовано | `#write`, `max-width`, width и центрирование применяются в layout. |
| F-010 | P3 | Частично | `TextLayout` даёт базу для bidirectional text; полноценный RTL-режим, настройки направления и RTL layout не реализованы. |

### 4.2. Блочные элементы

| ID | Приоритет | Статус | Что есть и чего не хватает |
|---|---:|---|---|
| F-020 | P0 | Частично | H1–H6, ATX/setext, скрытие маркеров и команды есть. Общей autoHeadingNumbering через тему/настройку нет; номера вычисляются только для явных `{#sec:…}`. |
| F-021 | P0 | Частично | Абзацы, physical/visual lines и word wrap работают. Полные Typora whitespace/line-break modes не реализованы. |
| F-022 | P0 | Реализовано | Цитаты, включая вложенные маркеры, отображаются с theme stripe. |
| F-023 | P0 | Частично | Bullet/ordered/nested lists и команды есть. `Enter` вставляет обычный newline, а `Tab`/`Shift+Tab` зарезервированы для таблиц; продолжения и indent/outdent списка нет. |
| F-024 | P0 | Реализовано | Task list и векторный checkbox изменяют один исходный символ через `Replace`. |
| F-025 | P0 | Реализовано | Thematic break отображается и вставляется командой. |
| F-026 | P0 | Частично | Fences, info string, простая theme-aware подсветка и source editing есть. Нет номеров строк, copy tool и автоотступа; подсветка не является полноценным language engine. |
| F-027 | P0 | Реализовано для редактора | Front matter card и source editing сохраняют исходный YAML. Properties меняет только `title`, `tags`, `draft`; схемы/autocomplete относятся к незакрытому R3. |
| F-028 | P1 | Частично | Есть Tab, add/remove row/column, alignment, copy/clear cell и точный source Replace. Нет перемещения строк/столбцов и автоматического создания таблицы по `\|a\|b\|` + `Enter`. |
| F-029 | P1 | Реализовано | JLaTeXMath display blocks, включая одно- и многострочные `$$…$$`; активный блок остаётся source. |
| F-030 | P2 | Частично | Footnotes распознаются parser/AST. Нет полноценного rendered footnote flow и hover с текстом определения. |
| F-031 | P2 | Не реализовано | Нет `[TOC]` widget и переходов по оглавлению. |
| F-032 | P3 | Частично | Без extension блок остаётся кодом; JDK-only `BlockRendererExtension` работает. Конкретных mermaid/sequence/flow renderers и demo plugin нет. |
| F-033 | P2 | Реализовано в минимальном варианте | HTML blocks показываются как код; inline `<u>…</u>` поддержан, прочий HTML не рендерится браузером. |
| F-034 | P2 | Не реализовано | Нет page-break marker/линии и связи с печатью/PDF. |
| F-035 | P3 | Не реализовано | Нет специального video/web placeholder-link поведения. |

### 4.3. Inline-форматирование

| ID | Приоритет | Статус | Что есть и чего не хватает |
|---|---:|---|---|
| F-040 | P0 | Реализовано | Bold, italic, strikethrough и `<u>` рендерятся и имеют команды; маркеры скрыты вне активного блока. |
| F-041 | P0 | Реализовано | Inline code и theme style поддержаны. |
| F-042 | P0 | Частично | Inline `[text](url)` и Ctrl/Cmd+Click для HTTP/HTTPS есть. Reference links в Canvas и открытие произвольных relative/file links этим путём не реализованы; wikilinks покрыты отдельным F-120. |
| F-043 | P1 | Частично | Поддержаны angle autolinks `<https://…>`. Голые URL без угловых скобок в `InlineMarkdown` не распознаются. |
| F-044 | P0 | Частично | Блочные images/Obsidian image embeds, resize, DnD, clipboard file/image и no-overwrite import есть. Смешанное изображение внутри текстового абзаца показывается как `🖼 alt`, а не как реальное inline image. |
| F-045 | P3 | Перенесено / плагин | Облачной загрузки изображений нет. |
| F-046 | P1 | Реализовано | Inline `$…$` рендерится JLaTeXMath как неделимый atom. |
| F-047 | P2 | Не реализовано | Нет emoji, sub/sup и highlight-опций. |
| F-048 | P2 | Не реализовано | Нет Smart Punctuation. |
| F-049 | P0 | Реализовано | Auto Pair для скобок, кавычек, backticks, `**`, `__`, skip closing и Backspace пустой пары работает в Canvas/Source. |

### 4.4. Правка, поиск, clipboard

| ID | Приоритет | Статус | Что есть и чего не хватает |
|---|---:|---|---|
| F-060 | P0 | Частично; полный объём в E9 | Есть plain text cut/copy/paste и image/file import. Нет Paste as Markdown vs plain text, HTML→Markdown и Copy as HTML. Evidence: `editor/view/ClipboardService.java`. |
| F-061 | P1 | Реализовано | Delete selection/current block и Delete Line применяют model commands. |
| F-062 | P0 | Перенесено в E9 | Нет Find/Replace, regexp/case/whole words и подсветки совпадений Canvas. Vault Search не заменяет внутриредакторный поиск. |
| F-063 | P2 | Не реализовано | Нет system/Hunspell spellcheck. |
| F-064 | P2 | Не реализовано | Нет пользовательских text snippets сокращение→шаблон. |
| F-065 | P2 | Не реализовано | Нет явной команды Reformat Markdown. Более того, смешанные EOL сейчас отклоняются при Save до появления явной нормализации. |
| F-066 | P0 | Реализовано | Words, Unicode code points, lines и reading time для selection/файла. Evidence: `editor/action/WordCount.java`, `EditorCommands.wordCount`. |
| F-067 | P0 | Реализовано для фиксированного профиля | Editor commands идут через ActionManager и Typora keymap. Настраиваемого пользовательского keymap GUI нет; это отдельный открытый пункт плана. |
| F-068 | P0 | Реализовано | Context-sensitive ПКМ через ActionGroup и EditorContext. |
| F-069 | P0 | Реализовано | Paragraph/Format/Insert доступны в меню и общей палитре. |

### 4.5. Оформление и темы

| ID | Приоритет | Статус | Что есть и чего не хватает |
|---|---:|---|---|
| F-080 | P0 | Реализовано | Typora-like CSS subset, каталог и девять тем. |
| F-081 | P1 | Реализовано | `base.user.css` и `{theme}.user.css` участвуют в каскаде. |
| F-082 | P1 | Реализовано | Локальные TTF/OTF `@font-face` с ограничением путей каталогом темы; WOFF не заявлен. |
| F-083 | P0 | Реализовано | CSS variables, Light/Dark оболочки и auto mapping `github`/`night`. |
| F-084 | P2 | Частично | Background color поддержан. Background image не поддержан. |
| F-085 | P2 | Частично | Есть вычисляемые Section numbers по явным `{#sec:…}`. Общей автонумерации всех headings и CSS-counters нет. |
| F-086 | P1 | Частично | `line-height`, margins/padding применяются из CSS и user CSS. Отдельной GUI-настройки интервалов нет. |
| F-087 | P1 | Реализовано | Sizes, fonts, margins, padding, borders, alignment и letter spacing участвуют в layout. |
| F-088 | P1 | Частично | Есть простая dark/light-aware палитра keywords/strings/comments. Отдельной полноценной настраиваемой code theme палитры нет. |
| F-089 | P1 | Частично; UI в E9 | `CssDiagnostic` собирается с источником/строкой и не роняет тему. Отдельной панели диагностик нет. |

### 4.6. Файлы, экспорт, интеграции

| ID | Приоритет | Статус | Что есть и чего не хватает |
|---|---:|---|---|
| F-100 | P0 | Частично; outline в E9 | Физическое дерево Vault и виртуальные views есть. Outline по заголовкам текущего редактора не подключён. |
| F-101 | P2 | Частично перенесено в E9 | Нет общего HTML/PDF/image export из AST/theme. HTML/PDF назначены E9, image export в текущем маршруте не назначен. Book Export создаёт только объединённый Markdown и не закрывает этот пункт. |
| F-102 | P3 | Перенесено / плагин | Нет Pandoc plugin и DOCX/EPUB/LaTeX export. |
| F-103 | P3 | Перенесено в E9 | Нет `Printable` и печати через общий layout. |
| F-104 | P2 | Не реализовано | CLI принимает только `--home` и `--smoke`; открытие переданного файла/папки не реализовано. Evidence: `CoreBootstrap.java`. |

### 4.7. Obsidian и собственные функции

| ID | Статус | Что есть и чего не хватает |
|---|---|---|
| F-120 | Реализовано | Wikilink alias, скрытие скобок и Vault-safe Ctrl/Cmd+Click через SPI/Core adapter. |
| F-121 | Реализовано | Image embed как image; note embed как редактируемая transclusion. |
| F-122 | Реализовано | Callout card с цветом/иконкой темы. Сворачивание callouts не заявлено этим ID и остаётся отдельным ограничением Obsidian-совместимости. |
| F-123 | Реализовано | `%%…%%` скрывается вне активного блока и связано с Notes. |
| F-M01 | Реализовано | Whole/section transclusion, Open original, Save в исходный файл и fresh read-merge-write. |
| F-M02 | Реализовано | Виртуальная Book из видимых view entries, chapter Save и no-overwrite Markdown export. |
| F-M03 | Реализовано | Явные sec/fig/tbl anchors, вычисляемые labels/captions и references без изменения исходника. |
| F-M04 | Реализовано как author-date subset | BibTeX, ограниченный CSL layout, groups/locators, completion и virtual bibliography. Это не полный CSL processor. |
| F-M05 | Реализовано | Правая Notes panel, Add/Edit/Select и общие undoable model operations. |
| F-M06 | Реализовано | Фоновая physical/virtual Branch Statistics. |
| F-M07 | Перенесено после Фазы 6 | Запуск code blocks через `McsFacade` отсутствует вместе с самим интерпретаторным контуром. |

## 5. Требования `REQUIREMENTS.md`

Этот документ старше текущего маршрута и содержит более широкий product scope. Поэтому статусы ниже нельзя выводить из отметок фаз `plan.md`.

### 5.1. Модель знаний и редактор

| ID | Приоритет | Статус | Фактическое состояние |
|---|---:|---|---|
| R1 | P0 | Частично | Физическая иерархия, виртуальные views, wikilinks и навигация есть. Графа связей нет. Виртуальная иерархия хранится в `.manuscriptium/views/*.yml`, а не только в Front Matter/пути, как предполагал раздел 5.3. |
| R2 | P0 | Реализовано | Обычные `.md` на диске являются источником истины; Vault открывается без импорта, SQLite/Lucene пересобираются. |
| R3 | P0 | Частично | YAML парсится для индекса; Properties безопасно редактирует три поля, Canvas показывает card/source. Нет schema validation, autocomplete полей и error highlighting в редакторе. |
| R4 | P0 | Реализовано | Live WYSIWYG-like Canvas, tables, task lists и простая code highlighting работают в одной вкладке с Source. |
| R5 | P1 | Частично | Теги есть; неизвестные произвольные поля сохраняются и показываются read-only. Нет полноценного редактирования arbitrary attributes и наследования от родителя. |
| R6 | P1 | Реализовано | Note/Tip/Warning/Danger и другие callout types получают card/style; folding не реализован. |
| R7 | P1 | Частично | Структурное табличное редактирование есть, но перемещение rows/columns и часть Advanced Tables behavior отсутствуют. |
| R8 | P2 | Не реализовано | Нет Excalidraw-like canvas/рисования. Markdown Canvas Editor не является графическим canvas этого требования. |

### 5.2. Навигация, поиск, метаданные

| ID | Приоритет | Статус | Фактическое состояние |
|---|---:|---|---|
| R9 | P0 | Частично | Lucene ищет content/title/path/front matter/tags, фильтрует draft/subtree и открывает результаты. Нет фильтров по произвольным Front Matter fields. |
| R10 | P0 | Частично | Command Palette есть и расширяется actions. Quick Switcher по всем заметкам отсутствует; Notes panel показывает только уже открытые вкладки. |
| R11 | P1 | Частично | Базовые деревья, lazy loading, popup и clone overlay есть. Folder Notes, pinned items, настраиваемой сортировки и отдельных счётчиков заметок нет. Branch Statistics не является таким счётчиком в дереве. |
| R12 | P1 | Не реализовано | Нет text extraction/search по PDF и изображениям. |
| R13 | P2 | Перенесено в Фазу 6 | Нет graph view и фильтров графа. |
| R14 | P0 | Не реализовано | Нет Dataview/Bases-like query engine и generated tables/lists. |
| R15 | P1 | Частично | Есть text fields для title/tags и checkbox draft. Нет generic Meta Bind controls, dropdowns, date fields и binding произвольных полей. |
| R16 | P1 | Не реализовано | Нет note-type Front Matter schemas и schema validation. |

### 5.3. Шаблоны, автоматизация, задачи

| ID | Приоритет | Статус | Фактическое состояние |
|---|---:|---|---|
| R17 | P0 | Не реализовано | Нет template engine с датой/заголовком/путём, Front Matter substitution и snippets. |
| R18 | P0 | Не реализовано | Есть обычное New Markdown Note, но нет QuickAdd по шаблону в выбранной папке с заполненными полями. |
| R19 | P1 | Не реализовано | Нет Linter, сортировки полей, Markdown normalization и on-save formatting. |
| R20 | P1 | Не реализовано | Нет Daily/periodic notes. |
| R21 | P1 | Частично | Markdown task lists и checkbox editing есть. Нет сроков, фильтров и единого Vault task dashboard. |
| R22 | P1 | Не реализовано | Нет calendar/reminders. |
| R23 | P2 | Перенесено в Фазу 6 | Kanban отсутствует. |

### 5.4. Синхронизация и репозитории

| ID | Приоритет | Статус | Фактическое состояние |
|---|---:|---|---|
| R24 | P0 | Частично | Можно последовательно открывать разные Vault, состояние каждого разделено. Нет одновременного управления несколькими репозиториями, отдельных Git remotes и sync settings для каждого. |
| R25 | P0 | Не реализовано | Нет SyncProvider, self-hosted server, Git/WebDAV/S3 sync backends. |
| R26 | P0 | Не реализовано | Нет раздельных режимов operational sync и Git backup/versioning. |
| R27 | P1 | Не реализовано | Нет общего text auto-merge/conflict UI и binary conflict dialog. Fresh-merge трансклюзий решает только локальный случай двух embed sessions. |
| R28 | P1 | Не реализовано | Локальная работа offline есть, но очереди изменений и отложенной отправки нет. |

### 5.5. Git, публикация, вложения

| ID | Приоритет | Статус | Фактическое состояние |
|---|---:|---|---|
| R29 | P0 | Не реализовано | JGit отсутствует. Фаза 5 предусматривает только commit/push в фоне; init, pull и auto-commit также нужно отдельно учесть, если R29 остаётся актуальным. |
| R30 | P0 | Не реализовано | Нет OS secure storage для tokens/SSH keys. Действующее требование не складывать секреты в репозиторий остаётся обязательным для будущей реализации. |
| R31 | P0 | Перенесено в Фазу 5 | Нет draft-gated publishing, article repository commit, Hugo trigger и publication result UI. |
| R32 | P0 | Перенесено в Фазу 5 | Нет pre-publish required-fields/links/attachments validation и ошибок в редакторе. |
| R33 | P1 | Перенесено | Нет PDF/HTML/EPUB profiles. HTML/PDF отнесены к E9; EPUB предполагается только через возможный Pandoc plugin. |
| R34 | P2 | Не реализовано | Нет Hugo/Astro/Next.js project templates. |
| R35 | P1 | Частично | Local file/image import в `assets` и clipboard PNG есть. Автоматической загрузки внешних remote images нет. |
| R36 | P1 | Частично | Relative paths и no-overwrite collisions есть. Нет attachment rename, dedup и общей link integrity проверки. |

### 5.6. Плагины, кастомизация, мобильность

| ID | Приоритет | Статус | Фактическое состояние |
|---|---:|---|---|
| R37 | P0 | Реализовано с переименованием | JPMS JAR, ModuleLayer, ServiceLoader и отдельный SDK есть. Вместо `Manuscriptium.api`/`graphite.api` используется модуль `ru.mcs.spi`. |
| R38 | P0 | Реализовано | Installed list, enable/disable, versions, dependencies, SDK compatibility и errors показаны в Settings. |
| R39 | P1/P2 | Реализовано для P1; каталог перенесён | Локальные folder/ZIP/JAR install и update есть. Remote plugin catalog отсутствует. |
| R40 | P1 | Частично | FlatLaf themes, theme selection и user CSS есть. Нет полноценного Style Settings GUI для пользовательских CSS properties; editor theme выбирается через service/state, а не отдельный GUI. |
| R41 | P1 | Частично | Плагины добавляют actions/groups/menus, которые попадают в Command Palette. Декларативной plugin keymap contribution и пользовательского keymap GUI нет. |
| R42 | P0 | Не реализовано | Нет mobile app/PWA/web client и self-hosted server. |
| R43 | P0 | Не реализовано | Нет mobile quick capture за 1–2 tap. |
| R44 | P1 | Не реализовано | Нет mobile reading/editing, offline cache и background sync. |

## 6. Нефункциональные требования

| ID | Статус | Фактическое состояние и пробелы |
|---|---|---|
| N1. Производительность | Частично / не подтверждено численно | Тяжёлые операции вынесены с EDT; есть 5 МиБ text test, incremental AST/layout и p95 frame test на 5000 строках. Нет задокументированных измерений cold start ≤2 с, 10k Vault ≤3 с, search ≤200 мс и общего UI stall ≤100 мс. |
| N2. Кроссплатформенность | Частично | Java 21, Swing и CI build/test/jlink на трёх ОС настроены; Windows/Linux запуски подтверждались в работе над этапами. Нет installers, отдельной Wayland-матрицы и зафиксированной полной приёмки на реальном macOS Intel/Apple Silicon. |
| N3. Надёжность | Частично | Atomic writes, strict UTF-8, BOM/EOL handling, save-point и background Save есть. Нет document autosave, version history и crash recovery. |
| N4. Приватность | Реализовано в текущем локальном контуре | Телеметрии и собственного network backend нет; Vault локален. HTTP links открываются по действию пользователя. Java-плагины имеют полный JVM access, поэтому этот пункт не является их sandbox guarantee. |
| N5. Безопасность плагинов | Частично | JPMS layers, exported SPI, permission declarations и явное full-JVM warning есть. Permissions не обеспечивают принудительную file/network capability isolation; процессной изоляции и подписей нет. |
| N6. Доступность | Частично | Keyboard actions/navigation и contrast themes есть. Нет Zoom, Canvas AccessibleContext и полноценной a11y-приёмки. |
| N7. Совместимость данных | Частично | Существующие Obsidian Vault открываются напрямую; Markdown, unknown YAML, BOM/EOL и symlinks защищены описанными правилами. Поддерживается подмножество Obsidian/Typora, а не вся экосистема; общего lossless export кроме Markdown Book нет. |
| N8. Обновления | Частично | Для внешних плагинов есть pending update и staging rollback при установке. Нет update checks ядра/плагинов и пользовательского отката на произвольную предыдущую версию. |

## 7. Архитектурные ограничения и их выполнение

| Ограничение / решение | Статус | Проверка |
|---|---|---|
| Multi-module Gradle Groovy, Java 21 | Реализовано | `settings.gradle`, корневой `build.gradle`, toolchain/release 21. |
| Граница `app → core → spi`; plugins не зависят от core | Реализовано | `markdown-editor` использует `compileOnly project(':spi')`; Obsidian доступен через SPI/Core adapter. |
| JPMS и отдельный ModuleLayer на плагин | Реализовано | `PluginManager`, module descriptors и ServiceLoader provider. |
| SPI только JDK/Swing | Реализовано | `spi/build.gradle` не имеет внешних dependencies; Flexmark/JLaTeXMath types не попадают в SPI. |
| Flexmark, CSS и JLaTeXMath как отдельные JAR plugins | Реализовано | `plugins/markdown-parser`, `plugins/editor-styles`, `plugins/math-renderer`. |
| Нет JavaFX/JCEF/WebView внутри редактора | Реализовано | Основной редактор — собственный Swing Canvas. JCEF оставлен только как возможный поздний plugin. |
| Нет JTextPane/JEditorPane/RSyntaxTextArea вместо Canvas | Реализовано | Canvas/Source используют `EditorCanvas`. Отдельный `JTextArea` внутри transclusion editor не заменяет основной Canvas. |
| Все основные изменения документа через `DocumentModel.apply` | Реализовано | Input, formatting, widgets, Notes и table/math/image mutations используют model commands. |
| Диск I/O и тяжёлый rendering вне EDT | Реализовано по основным путям | SwingWorker/executors, parser coordinator, ImageCache, math/extensions, citations и Branch Statistics. |
| Атомарная Markdown запись и сохранение symlink target | Реализовано | `AtomicFileStore` SPI и `AtomicFileStoreImpl`. Properties намеренно использует более строгий no-symlink policy. |
| Unknown YAML не перезаписывается | Реализовано для управляемого subset | Canvas сохраняет исходник; Properties меняет только title/tags/draft и сохраняет unknown YAML/body. |
| Смешанные EOL не нормализуются молча | Реализовано | Save отклоняет mixed EOL; явной normalize команды пока нет. |
| Создание/export без overwrite и защита Vault paths | Реализовано в существующих операциях | `CREATE_NEW` для notes/Book export, path/symlink checks в Vault/Obsidian/image operations. |
| SDK 0.9.0 / manifest 0.9 | Реализовано | Аддитивные SPI-контракты не меняли текущую линию совместимости. |
| Плагин имеет полный JVM access, установка требует предупреждения | Реализовано | `PluginInstallDialog` требует явного acceptance; `permissions` остаются декларацией, а не sandbox. |
| JUnit 5 + AssertJ, golden/headless tests | Реализовано | Тесты модулей, DrawSink/metrics, девять Linux goldens и packaged smoke. |
| CI Windows/Ubuntu/macOS | Конфигурация реализована | Workflow существует, но текущий remote run и расхождение smoke-строки с math plugin отдельно не подтверждены. |

## 8. Расхождения между документами и актуальной архитектурой

### 8.1. `REQUIREMENTS.md` содержит исторический черновик

Следующие названия и решения заменены актуальным `plan.md` и кодом:

| В `REQUIREMENTS.md` | Актуальное состояние |
|---|---|
| Graphite, `graphite.api`, `graphite.core`, `graphite.*` | Manuscriptium, `ru.mcs.spi`, `ru.mcs.core`, `ru.mcs.app`, `ru.mcs.plugin.*`. |
| Java 17 | Java 21 для всего проекта. |
| JTextPane/RSyntaxTextArea как редактор | Собственный lightweight Canvas; старый выбор неактуален. |
| JCEF/Lobo как штатный HTML preview | Browser preview в основной редактор не встроен; JCEF возможен только как поздний отдельный plugin. |
| `plugin.json` | `plugin.yml` с `sdk-version`, dependencies, permissions и contributions. |
| `.graphite/vault.json` | `<vault>/.manuscriptium/` с `index.db`, `lucene/`, `state/`, `views/` и другими служебными данными. |
| Иерархия/атрибуты только в Front Matter и path | Виртуальная иерархия дополнительно хранится как ссылки в `.manuscriptium/views/*.yml`. |
| Jackson configuration | В текущем core используются SnakeYAML и собственные JSON/state helpers. |
| SQLite FTS5 как быстрый поиск | SQLite хранит snapshot/index, полнотекстовый поиск выполняет отдельный Lucene index. |
| TestFX | JUnit 5/AssertJ, headless Swing tests, rendering tests и packaged acceptance; TestFX не подключён. |

### 8.2. Текущий маршрут уже изменил исходный MVP

`REQUIREMENTS.md` называет P0 и MVP одновременно дерево/редактор, Quick Switcher, шаблоны/QuickAdd, несколько репозиториев, sync/Git/publishing и mobile quick capture. Актуальный `plan.md` сначала строит оболочку и E0–E9, затем только publishing и extensions. Следовательно:

- текущие завершённые фазы не означают выполнение исходного MVP;
- Фаза 5 покрывает лишь часть R29–R33 и не покрывает самостоятельно sync/mobile/templates;
- перед следующим планированием нужно либо подтвердить новый состав MVP, либо явно сохранить старые P0 как отдельные этапы.

### 8.3. Локальные расхождения внутри editor documents

- F-002 требует кнопку Source Mode в status bar; фактически переключатель находится внутри Markdown-вкладки. Поведение есть, размещение отличается.
- F-020/F-085 шире F-M03: явная sec/fig/tbl-нумерация не равна автоматической нумерации всех заголовков.
- F-043 требует голые URL; текущий E5-text «autolinks» фактически означает angle autolinks и не должен считаться полным выполнением F-043.
- F-060 и F-062 имеют P0, но перенесены в E9. Это осознанное изменение маршрута, а не завершённые P0.
- F-100 разделён на готовый Vault tree и отсутствующий editor outline.
- E4/E8 зафиксированы как завершённые в объёме этапа, но CSS diagnostics UI и полный CSL не входят в этот объём. Это ограничения scope, а не скрытые готовые функции.
- В `README.md` отдельно указано отсутствие UI выбора editor theme; наличие `EditorStyleService.selectTheme` не следует ошибочно считать готовым GUI.

## 9. Критерии исходного MVP

| Критерий из `REQUIREMENTS.md` | Статус |
|---|---|
| За 5 минут создать заметку, написать статью, commit и publish без сторонних plugins | Не достигнуто: создание и редактирование есть, Git/publish отсутствуют. |
| Cold start ≤2 с и search 10k ≤200 мс | Не подтверждено измерениями. |
| Внешний plugin по SDK docs устанавливается без пересборки core | Реализовано: standalone template и external packaged smoke. |
| Мобильная заметка появляется на desktop без ручных действий | Не реализовано. |

## 10. Сводный реестр незакрытой работы

Этот список не назначает новый порядок этапов; он только собирает пробелы из таблиц для последующей ручной правки плана.

### 10.1. Явно назначенные E9

- F-007: document autosave и version history/recovery.
- F-008: Zoom Canvas.
- F-060: HTML→Markdown, режимы paste и Copy as HTML.
- F-062: Find/Replace с regexp/case/words и Canvas highlights.
- F-100: outline по заголовкам.
- F-101/F-103: HTML/PDF export, Exporter SPI и print; image export из F-101 в E9 не назначен.
- Доступность Canvas через `AccessibleContext`.
- UI журнала CSS diagnostics.
- Производительность 50-страничного документа и общая полировка.

### 10.2. Частичные функции редактора, не исчерпанные E0–E8

- F-010: полноценный RTL.
- F-020/F-085: общая heading numbering.
- F-021: whitespace/line-break modes.
- F-023: list continuation по Enter и Tab/Shift+Tab indent/outdent.
- F-026: line numbers, copy tool, автоотступ и более полная code highlighting.
- F-028/R7: перемещение rows/columns и pipe+Enter creation.
- F-030: rendered footnotes и hover definitions.
- F-032: реальные diagram renderers поверх готового extension SPI.
- F-042/F-043: reference links, bare URLs и более широкая навигация обычных links.
- F-044: настоящие mixed inline images.
- F-084/F-086/F-088: background image, GUI spacing settings и полноценная code theme palette.
- F-089: diagnostics UI.

### 10.3. Функции каталога без текущей реализации

- F-006 Strict Mode.
- F-031 `[TOC]`.
- F-034 page breaks.
- F-035 video/web placeholders.
- F-047 emoji/sub/sup/highlight.
- F-048 Smart Punctuation.
- F-063 spellcheck.
- F-064 text snippets.
- F-065 Reformat Markdown/явная EOL normalization.
- F-104 открытие file/folder из CLI.

### 10.4. Публикация и Git

- Hugo `StaticSiteExporter`, `PublishingProfile`, YAML/link/image conversion и multilingual publishing.
- Draft gate, required fields, broken links/attachments и ошибки в редакторе.
- JGit commit/push, а для полного R29 также init/pull/auto-commit.
- OS secure storage для tokens/SSH keys.
- PDF/HTML/EPUB profiles и project templates для SSG.

### 10.5. Более широкий product scope из `REQUIREMENTS.md`

- Полный Front Matter schema/validation/autocomplete/error highlighting и arbitrary attributes inheritance.
- Quick Switcher, advanced navigator и attachment search.
- Dataview/Bases-like queries.
- Meta Bind-like generic controls.
- Templates, QuickAdd, Linter, Daily Notes.
- Task dashboard, calendar/reminders.
- Несколько одновременно управляемых репозиториев с независимыми remotes/settings.
- Sync backends, offline queue, conflict resolution и разделение sync/backup.
- Attachment rename/dedup/integrity и remote image localization.
- Remote plugin catalog, Style Settings GUI, generic plugin keymap contributions.
- Mobile/PWA client и quick capture.
- Core/plugin update checks и пользовательский rollback.
- Численная N1/N2 acceptance и crash recovery.

### 10.6. Явно отложенные расширения

- `McsFacade` scripts и F-M07 code execution.
- AI assistant.
- JCEF plugin.
- Graph и Kanban.
- SyncProvider/E2EE как extension architecture.
- Runtime Java compilation.
- Pandoc, cloud image upload и другие P3 plugins.
- Remote plugin repository.
- Editor splits, floating/draggable panels и настраиваемый keymap GUI.

## 11. Итоговая граница текущего состояния

Текущий Manuscriptium — работающая расширяемая desktop-оболочка с локальным Markdown Vault, поиском, виртуальными иерархиями и существенным собственным Canvas-редактором, включая Obsidian- и manuscript-функции E8.

Он пока не является завершённым Typora-совместимым редактором по всему каталогу и не реализует исходный MVP «Front Matter + Git + публикация + синхронизация + mobile» из `REQUIREMENTS.md`. Следующий план можно строить от этой границы, не смешивая три разных утверждения: «этап E8 завершён», «весь редактор завершён» и «исходный продуктовый MVP завершён». Первое подтверждено; второе и третье — нет.
