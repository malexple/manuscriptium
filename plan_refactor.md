# Manuscriptium — план архитектурного рефакторинга

Дата: 2026-11-19. Статус: шаги 1–12 завершены; полная автоматическая и ручная приёмка на Linux пройдена. Удалённый CI Ubuntu/Windows/macOS в этом срезе не запускался.

## 1. Цель и границы

Цель — стабилизировать существующий редактор и разделить приложение на универсальную платформу, универсальный текстовый редактор, декларативные метаданные и самостоятельный языковой модуль Markdown.

Во время рефакторинга не добавляем новые продуктовые функции. Все существующие пользовательские сценарии должны продолжать работать. Каждый шаг завершается сборкой, автоматическими тестами и проверкой затронутых сценариев. Следующий шаг не начинается при красной сборке.

### Целевые Gradle-модули

| Модуль | JPMS | Назначение |
|---|---|---|
| `spi` | `ru.mcs.spi` | Базовые контракты платформы без Swing, AWT и Markdown. |
| `spi-swing` | `ru.mcs.spi.swing` | Swing-контракты десктопного клиента. |
| `editor` | `ru.mcs.editor` | Универсальная текстовая модель и редактор любых текстовых файлов. |
| `metadata` | `ru.mcs.metadata` | Описания приложения, формат данных, загрузка и валидация описаний; без исполнения и отображения. |
| `markdown` | `ru.mcs.markdown` | Вся встроенная поддержка Markdown: parser, AST, editor, layout, renderer, styles, math, properties и notes. |
| `core` | `ru.mcs.core` | Платформа, проект, индекс, поиск, оболочка, сервисы и реализация десктопного интерфейса. |
| `app` | `ru.mcs.app` | Запуск и сборка продукта Manuscriptium. |

Новые физические модули: `spi-swing`, `editor`, `metadata`, `markdown`. Модуль `markdown` формируется путём объединения существующей Markdown-реализации, а не написания новой с нуля.

Не создаём отдельные модули `project`, `util`, `spi-editor`, `editor-canvas`, `desktop-swing`, `lang-markdown-api`, `product-minimal`, `product-ai` или `product-zig`.

### Направление зависимостей

| Модуль | Разрешённые зависимости |
|---|---|
| `spi` | Только JDK, без `java.desktop`. |
| `spi-swing` | `spi`, `java.desktop`. |
| `metadata` | Только JDK; идентификаторы команд и компонентов являются данными. |
| `editor` | `spi`, `spi-swing`, `metadata`, `java.desktop`; без `core` и `markdown`. |
| `core` | `spi`, `spi-swing`, `metadata`; без `editor` и `markdown` в production-зависимостях. |
| `markdown` | `spi`, `spi-swing`, `editor`, `metadata`; без `core`. |
| `app` | Собирает `core`, `editor`, `markdown` и запускает продукт. |

Доступ `markdown` к проекту, индексу, открытию файлов и сервисам оболочки осуществляется только через универсальные контракты `spi`/`spi-swing`. Прямая зависимость `markdown → core` запрещена.

### Что не входит в рефакторинг

- публикация, Git, синхронизация и новые правила Front Matter;
- Kotlin/KMP, Android и iOS;
- AI, Zig, LSP и новые языковые модули;
- универсальный VFS/PSI/remote development по образцу IntelliJ;
- добавление возможностей из прежнего E9, кроме исправления уже заявленного поведения;
- изменение формата `.manuscriptium/`, views, SQLite/Lucene или Markdown-файлов без необходимости миграции состояния Project.

### Сознательные изменения, которые не следует принимать за скрытую потерю функций

1. Встроенные `markdown-parser`, `markdown-editor`, `editor-styles`, `math-renderer`, `properties` и `notes` перестают быть отдельными bundled-плагинами. Они исчезнут из списка плагинов и не будут отключаться по отдельности; их пользовательские возможности остаются в модуле `markdown`.
2. `plugins:hello` удаляется вместе с демонстрационными действиями.
3. Бинарная совместимость старых внешних JAR со старым SPI не сохраняется. Шаблон внешнего плагина и smoke-тест обновляются; возможность устанавливать внешние JAR остаётся.
4. Старые плановые документы и patch-файлы удаляются только после переноса актуальных решений и успешной финальной приёмки.
5. Поведение Markdown, изображений, формул, таблиц, ссылок, Properties, Notes, Book и поиска не должно ухудшиться.

## 2. Общий инвариант сохранения функциональности

До начала переносов формируется реестр существующих тестов и пользовательских сценариев. При перемещении теста сохраняется его смысл; удаление теста допускается только при эквивалентной замене с объяснением в описании среза.

Обязательны к сохранению:

- запуск на Windows, Linux и macOS, jlink image и packaged smoke;
- установка, включение/отключение и обновление внешних JAR-плагинов;
- открытие проекта, физическое дерево, виртуальные виды, клоны, Book, поиск и индексация;
- Canvas/Source, каретка, выделение, IME, clipboard, Enter, undo/redo, Save и save-point;
- Focus, Typewriter, Auto Pair, меню, контекстные действия, палитра и Typora keymap;
- таблицы, изображения, формулы, callouts, Front Matter и extension-rendered блоки;
- Wikilinks, Embeds, Obsidian comments, cross references, citations и bibliography;
- Properties, Notes и Branch Statistics;
- девять editor themes, светлая/тёмная темы приложения и ru/en;
- атомарная запись, сохранение Markdown symlink target, Vault/Project path checks, `CREATE_NEW` и защита неизвестного YAML;
- fresh-merge трансклюзий, защита от устаревших фоновых результатов и отсутствие тяжёлого дискового I/O на EDT.

Обязательные security-инструкции:

- Для Java-плагинов песочницы нет: плагин имеет полный доступ к JVM, установка требует предупреждения пользователя.
- токены и SSH-ключи должны храниться в защищённом хранилище ОС и никогда не попадать в репозиторий
- Markdown-операции не должны перезаписывать существующие файлы при создании или выходить за пределы Vault.
- Obsidian-навигация отклоняет выход за Vault и symlink в target/родителях.
- Properties отклоняет symlink, использует NOFOLLOW_LINKS, temp-файл и атомарную замену только по Apply.
- Новый YAML view создаётся без перезаписи существующего файла.
- Lucene и SQLite — пересобираемые кэши внутри .manuscriptium; они не сканируются как содержимое Vault.
- Для Markdown editor symlink разрешён и должен вести запись в target без замены самой ссылки.

После переименования Vault в Project правила безопасности не ослабляются.

### Команда полной автоматической проверки после каждого среза

```bash
./gradlew clean test :app:devHome :app:jlink
```

Дополнительно запускаются golden/rendering tests, packaged smoke и ручная приёмка изменённого сценария. Полный clean build не заменяется компиляцией одного модуля.

## 3. Пошаговый план

### Шаг 1. Зафиксировать базовое поведение

**Статус: автоматическая база подтверждена 2026-11-16.**

- `clean test :app:devHome :app:jlink`: 132 test suites, 502 теста, 0 failures/errors/skipped; jlink image собран.
- Девять Linux golden-тем входят в общий зелёный прогон; эталоны не обновлялись.
- Bundled packaged smoke: `failures={}`.
- Standalone plugin-template и external packaged smoke: `failures={}`, вклады `ru.mcs.plugin.template` обнаружены.
- Подтверждено расхождение `.github/workflows/build.yml`: ожидаемые smoke-строки не содержат `ru.mcs.plugin.math`, хотя фактический packaged smoke его загружает. Исправление ожиданий выполняется при изменении состава модулей.
- Последняя полная пользовательская приёмка E8 ранее подтверждена; после baseline-проверки production-код не изменялся.

| Текущие тесты | Suites | Тесты | Целевой модуль |
|---|---:|---:|---|
| `core` | 73 | 314 | `core`, затем `editor` для универсальных редакторских тестов |
| `plugins/markdown-editor` | 42 | 127 | `editor` и `markdown` по фактической языковой зависимости |
| `plugins/editor-styles` | 6 | 24 | `markdown` |
| `plugins/markdown-parser` | 2 | 11 | `markdown` |
| `plugins/math-renderer` | 1 | 1 | `markdown` |
| `plugins/notes` | 3 | 7 | `markdown` |
| `plugins/properties` | 3 | 15 | `markdown` |
| `plugins/hello` | 2 | 3 | Удаляются вместе с Hello |

**Изменения**

1. Запустить полную проверку исходного состояния и зафиксировать количество тестов, golden-наборы и smoke-результаты.
2. Составить таблицу «существующий тест → целевой модуль» и «пользовательская функция → проверка».
3. Записать воспроизводимые дефекты редактора отдельным списком. Не смешивать исправление дефекта с переносом пакетов.
4. Для дефектов без автоматического покрытия сначала добавить characterization/regression tests на текущей архитектуре.
5. Проверить CI-конфигурацию bundled smoke до изменения состава модулей.

**Автоматические проверки**

- полный baseline build без failures/errors;
- все существующие тесты учтены в реестре;
- девять Linux golden-тем проходят без обновления эталонов;
- bundled и external smoke выполняются.

**Ручная приёмка**

- приложение запускается из packaged image;
- открытие проекта, создание/открытие Markdown, Canvas/Source, Save и поиск работают до начала переносов.

**Готово, когда:** есть зелёная база и проверяемый список поведения, которое нельзя потерять.

### Шаг 2. Разорвать циклы без изменения модульной структуры

**Статус: завершён и проверен 2026-11-16.**

- Устранены целевые обратные зависимости `MacOsSupport`, `i18n → plugin`, `theme ↔ settings`, `core.editor ↔ core.vault` и циклы на границах `markdown-editor` action/mode/layout/render/view.
- Добавлены `CorePackageDependenciesTest` и `EditorPackageDependenciesTest`: всего 134 test suites, 507 тестов, 0 failures/errors/skipped. Прирост к baseline — ровно пять архитектурных тестов.
- `clean test :app:devHome :app:jlink` завершилась успешно; девять Linux golden-тем прошли без обновления эталонов.
- Bundled и external packaged smoke завершились с `failures={}`; внешний `plugin-template` собран и загружен отдельно.
- На реальном packaged image проверены меню Paragraph/Format/Insert/View, ПКМ, палитра, Focus Mode, Typewriter Mode, Ctrl+Click-навигация в `Target.md`, Light/Dark через Settings и сохранение выбранной темы.

**Изменения**

1. Перенести `MacOsSupport` в подходящий платформенный пакет и убрать обратные ссылки на корневой пакет `core`.
2. Изменить `PluginMessages`, чтобы i18n не зависел от `PluginDescriptor`.
3. Ввести узкий `ThemeController`, разорвав `settings ↔ theme` без изменения поведения настроек.
4. Разорвать `editor ↔ vault`: общие файловые/проектные типы не должны зависеть от редактора.
5. В `markdown-editor` отделить общие draw/model-типы от layout/view; `TyporaKeymap` и `TypewriterMode` должны зависеть от интерфейса контекста, а не конкретного `EditorCanvas`.
6. Не требовать отсутствия всех внутренних пакетных циклов; устранять циклы на границах `core/editor/project`, `model/view/render` и будущих модулей.

**Автоматические проверки**

- package dependency tests фиксируют разрешённые направления;
- тесты тем, настроек, i18n, навигации, Focus/Typewriter и keymap проходят;
- полный build и packaged smoke зелёные.

**Ручная приёмка**

- смена Light/Dark и editor theme работает;
- меню, ПКМ, палитра, Focus и Typewriter не изменились;
- навигация по ссылке открывает прежний файл.

**Готово, когда:** целевые модули можно вводить без циклических зависимостей.

### Шаг 3. Переименовать Vault в Project внутри `core`

**Статус: завершён и проверен 2026-11-16.**

- Пакет `core.vault`, классы `Vault*`, `Scope.VAULT`, action/tool-window IDs, i18n, README и SDK-документация переведены на Project. В production-коде старый идентификатор остался только как явный legacy state id `core.vault` для миграции.
- `StateManagerImpl` читает `core.vault.yml` только при отсутствии `core.project.yml`; новый файл создаётся при сохранении, legacy-файл остаётся нетронутым. При одновременном наличии приоритет у нового состояния.
- Добавлены `ProjectStateMigrationTest`, `ProjectCompatibilityTest` и архитектурная проверка старых идентификаторов. Полный прогон: 136 test suites, 511 тестов, 0 failures/errors/skipped; все прежние тесты сохранены, девять golden-тем зелёные без обновления эталонов.
- `clean test :app:devHome :app:jlink` успешна. Bundled smoke содержит `core.project` и завершился с `failures={}`; standalone plugin-template и external packaged smoke также зелёные.
- На packaged image подтверждены открытие существующего проекта по legacy state, создание `inbox/created.md`, поиск, существующий view, клонирование ссылки с записью YAML, явное Open Project со сменой каталога и возврат, восстановление вкладок и повторный запуск уже по `core.project.yml`. `.manuscriptium`, session, views, Markdown, SQLite/Lucene сохранены.

**Изменения**

1. `core.vault` → `core.project`; классы `Vault*` → соответствующие `Project*`.
2. Обновить действия, tool windows, i18n, настройки, session/state и идентификаторы UI.
3. `Scope.VAULT` → `Scope.PROJECT`; compatibility alias не оставляем.
4. Последний открытый проект читается из старого состояния `core.vault` и сохраняется как `core.project`.
5. Не переименовывать `.manuscriptium/`, `views/`, `index.db`, `lucene/` и существующие session-файлы проекта.
6. Обновить тесты, README и SDK-документацию, не меняя формат пользовательских данных.

**Автоматические проверки**

- `ProjectStateMigrationTest`: старое состояние открывается и сохраняется под новым id;
- `ProjectCompatibilityTest`: существующий `.manuscriptium` не пересоздаётся и не теряется;
- тесты физического дерева, видов, клонов, Book, watcher, SQLite/Lucene и поиска проходят;
- production-код `core` не содержит старых `Vault`-идентификаторов;
- полный build и smoke зелёные.

**Ручная приёмка**

- открыть существующий проект до миграции состояния и повторно после него;
- проверить Open Project, создание заметки, поиск, вид, клон и восстановление вкладок.

**Готово, когда:** пользователь видит Project, данные и сессия прежнего Vault доступны без ручного восстановления.

### Шаг 4. Выделить `spi-swing`

**Статус: завершён и проверен 2026-11-17.**

- Создан JPMS-модуль `ru.mcs.spi.swing`; 29 production-файлов со Swing/AWT-контрактами перенесены из `spi` в пакеты `ru.mcs.spi.swing.*`. `spi` больше не требует `java.desktop`, split packages отсутствуют.
- Обновлены Gradle/JPMS-зависимости, bundled-плагины, service declarations, standalone `plugin-template`, README и SDK-документация. Старые Swing-сигнатуры в `spi` не сохранялись: внешние плагины необходимо пересобрать против нового SDK.
- Добавлены `SpiPurityTest` и `SpiSwingModuleTest`, а также regression-тесты restart-handshake и повторного открытия пересобираемых кэшей. Полный `clean test :app:devHome :app:jlink`: 138 test suites, 522 теста, 0 failures/errors/skipped; девять Linux golden-тем прошли без обновления эталонов.
- Standalone `plugin-template` собран. Bundled и external packaged smoke завершились с `failures={}`; `ru.mcs.plugin.math` и внешний `ru.mcs.plugin.template` присутствуют в фактическом порядке загрузки, `template.hello` и `toolwindow.toggle.template.panel` зарегистрированы. CI-проверки приведены к этому составу и дополнены проверками `ru.mcs.spi` и `ru.mcs.spi.swing`.
- На packaged image подтверждены Settings, Plugins, Install from disk, предупреждение о полном доступе плагина к JVM, установка внешнего JAR и restart prompt, Template/Hello/Notes/Properties tool windows, Project tree, вкладки, Canvas, status bar и Notifications.
- Restart переведён на кроссплатформенный handshake по PID через `ProcessHandle`: новый процесс ждёт завершения прежнего до инициализации AWT, плагинов, SQLite и Lucene. Дополнительно первичное открытие Project выполняет один retry только после `IOException`; первая попытка закрывается, `SQLException` и runtime errors не повторяются. Два restart на tmpfs и два на исходной FUSE-ФС прошли; во втором FUSE-прогоне retry реально восстановил открытие после `EIO` в Lucene commit.

**Изменения**

1. Создать модуль `spi-swing` и перенести из `spi` типы, содержащие `JComponent`, AWT/Swing `Icon` и другие desktop UI-типы.
2. В `spi` оставить платформенно-нейтральные контракты, идентификаторы, DTO и сервисные интерфейсы.
3. Обновить `module-info`, Gradle dependencies, core, editor candidates, plugin-template и тесты.
4. Не сохранять старые Swing-сигнатуры в `spi` ради бинарной совместимости.
5. Обновить внешний плагин-шаблон, сохранив сам сценарий установки JAR.

**Автоматические проверки**

- `SpiPurityTest`: classfiles `spi` не ссылаются на `java.awt.*`, `javax.swing.*` и не требуют `java.desktop`;
- `SpiSwingModuleTest`: публичные Swing-контракты доступны из `ru.mcs.spi.swing`;
- JPMS/jlink собирает оба модуля без split packages;
- plugin-template компилируется против нового SDK;
- external packaged smoke успешно загружает пересобранный JAR;
- полный build и существующие UI-тесты зелёные.

**Ручная приёмка**

- Settings, Plugins, tool windows, status bar, вкладки и уведомления выглядят и работают как раньше;
- установка внешнего плагина с перезапуском работает.

**Готово, когда:** `spi` не зависит от desktop UI, а весь прежний Swing-интерфейс доступен через `spi-swing`.

### Шаг 5. Выделить универсальный `editor`

**Статус: завершён 2026-11-17.**

- Создан JPMS-модуль `ru.mcs.editor`; языконезависимые model, draw, input, caret/selection, viewport, IME, clipboard,
  mouse/keyboard handling и TypewriterMode перенесены из `plugins/markdown-editor`.
- `MarkdownCanvas extends EditorCanvas`; Markdown AST/layout/render/widgets остаются в языковом плагине. Обратной
  зависимости `editor → markdown` нет, `EditorPurityTest` проверяет production classfiles и JPMS provides/requires.
- Обычные файлы до 5 МиБ открывает `editor.text` через `ServiceLoader<FileEditorProvider>`; `core` не зависит от `editor`.
  Асинхронные загрузка/Save, strict UTF-8, BOM/EOL, save-point, atomic write и symlink-target Save покрыты тестами.
- Прежний `JTextArea` оставлен только как `markdown.fallback` для `.md` с Obsidian-навигацией при отключённом
  Markdown-плагине. Восстановлена привязка Ctrl+Y для redo вместе с Ctrl+Shift+Z.
- Полная проверка: 140 test suites, 531 тест, 0 failures/errors, 2 осознанных `@Disabled`; девять golden-тем, jlink,
  standalone plugin-template, bundled и external packaged smoke прошли.
- Packaged UI-приёмка: plain text и Markdown Source/Canvas, ru/en ввод, длинная строка, selection, copy/cut/paste,
  undo/redo, Save, Dirty → Cancel и чистое закрытие прошли на реальном jlink image.

**Изменения**

1. Создать `editor` и перенести из `plugins/markdown-editor` языконезависимые части:
   - `model`: DocumentModel, piece table, Position, Range, EditCommand, undo/redo;
   - универсальные `view`, `input`, caret, selection, viewport, IME, clipboard и mouse/keyboard handling;
   - базовый Source Canvas и общие draw/text-measuring абстракции.
2. Оставить Markdown AST, MarkdownLayoutEngine, block/inline Markdown rendering и Markdown widgets в текущем редакторе до следующего шага.
3. Определить публичный API `editor` для языковых модулей; внутренние реализации не экспортировать без необходимости.
4. Перевести Markdown-редактор на универсальный `editor`, не добавляя зависимость `editor → markdown`.
5. Обеспечить Source Mode для обычного текстового файла через тот же редактор. Поведение очень больших файлов отдельно измерить, не вводя заранее второй редактор.
6. Исправления из реестра дефектов выполнять отдельными коммитами/срезами внутри шага, каждое — после воспроизводящего теста.

**Автоматические проверки**

- все piece table, transaction, undo/redo, UTF-16, BOM/EOL и save-point tests перенесены и проходят;
- caret, selection, Enter, clipboard, IME, keymap и scrolling tests проходят;
- `EditorPurityTest`: production `editor` не содержит идентификаторов `markdown`, `obsidian`, `frontmatter` и не зависит от `core`;
- обычный `.txt` открывается, редактируется и сохраняется через `editor`;
- существующие Markdown layout/golden tests проходят без обновления эталонов;
- полный build и packaged smoke зелёные.

**Ручная приёмка**

- проверить обычный текст и один Markdown: ввод, Enter, Delete, выделение, copy/cut/paste, undo/redo, Save, закрытие с несохранёнными изменениями;
- проверить русский и английский ввод, длинные строки и перенос строк;
- убедиться, что каретка и прокрутка не меняют поведение после переключения Source/Canvas.

**Готово, когда:** универсальный редактор работает без Markdown, а Markdown использует его без потери редактируемости.

### Шаг 6. Объединить встроенную Markdown-реализацию в `markdown`

**Статус: завершён и проверен 2026-11-17.**

- Создан JPMS-модуль `ru.mcs.markdown`; встроенные `markdown-parser`, `markdown-editor`, `editor-styles`, `math-renderer`,
  `properties` и `notes` перенесены в него вместе с реализацией, ресурсами, девятью CSS-темами, golden/corpus/fonts и тестами.
  Hello и все семь прежних bundled-plugin каталогов удалены. JPMS экспортирует только `ru.mcs.markdown.api`; Flexmark и
  JLaTeXMath отсутствуют в публичных JVM-сигнатурах, `markdown → core` и `core → markdown` запрещены architecture tests.
- Добавлен контракт `BuiltInModule`: `app` явно передаёт `MarkdownModule` в `CoreBootstrap`. Встроенный модуль использует
  `PluginContext` и автоматическую очистку регистраций, но не имеет `plugin.yml`, отдельного `ModuleLayer`, `PluginState`
  или переключателя в Settings → Plugins. Действия, редактор, settings, Properties и Notes регистрируются напрямую без
  declarative proxy; state ids — `markdown.editor` и `markdown.style`.
- Flexmark/JLaTeXMath включены в jlink image как automatic modules; прежние system-plugins больше не создаются.
  `MarkdownModuleBoundaryTest`, `CoreBuiltInModulesTest`, module-resource i18n и package dependency tests фиксируют границу
  и lifecycle, включая cleanup после исключения при старте.
- Финальная проверка `clean test :app:devHome :app:jlink`: 140 test suites, 535 тестов, 0 failures/errors, 1 перенесённый
  без включения пользовательский `@Disabled`; девять Linux golden-тем прошли без обновления эталонов. Standalone
  `plugin-template clean build`, bundled smoke (`builtIns=[markdown] plugins=[]`) и external smoke
  (`builtIns=[markdown] plugins=[ru.mcs.plugin.template]`) успешны; `failures={}` и `builtInFailures={}`.
- Исправлен запуск через `:app:run`: Flexmark без `Automatic-Module-Name` не перемещался Gradle на module path.
  Теперь `run` явно передаёт весь `runtimeClasspath` через `--module-path`; отдельный `:app:run --args="--smoke"`
  добавлен в CI вместе с проверками встроенного Markdown и editor providers.
- Packaged UI-приёмка на реальном jlink image: сложный Markdown с Front Matter, citation, callout, таблицей, формулой,
  изображением, wikilink и transclusion; Canvas/Source и Save с файловыми assertions; Properties Apply с сохранением
  unknown YAML; Notes Add/Edit; выбор `doe2020` в citation completion; Classification/Open as Book и Branch Statistics.
  Hello отсутствует. Внешний Template имеет действие и tool window, отключается через Settings → Plugins с restart;
  после его отключения встроенный Markdown продолжает работать.

**Изменения**

1. Создать `markdown` с пакетами `api`, `parser`, `ast`, `editor`, `layout`, `render`, `widget`, `style`, `reference`, `frontmatter`, `properties`, `notes` и `math`.
2. Перенести в него `plugins:markdown-parser`, `plugins:markdown-editor`, `plugins:editor-styles` и `plugins:math-renderer`.
3. Перенести `plugins:properties` и `plugins:notes`, поскольку их текущая реализация работает с Markdown Front Matter и Markdown comments.
4. Удалить отдельные plugin descriptors и `provides ru.mcs.spi.swing.Plugin` для объединённых встроенных модулей. Внешние плагины продолжают загружаться через `core`.
5. Экспортировать через JPMS только публичный `ru.mcs.markdown.api` и действительно необходимые пакеты. Flexmark и JLaTeXMath не появляются в сигнатурах SPI.
6. Перенести ресурсы, иконки, CSS, i18n и тесты вместе с реализацией.
7. Удалить `plugins:hello`, его манифест, ресурсы, тесты и включение в сборку.
8. Обновить `settings.gradle`, `app/build.gradle`, system-plugins и smoke-ожидания. Встроенный Markdown больше не копируется как набор plugin JAR.

**Автоматические проверки**

- все тесты parser, AST, layout, renderer, styles, math, images, tables, transclusions, citations, Properties и Notes выполняются в `markdown`;
- `MarkdownModuleBoundaryTest`: `markdown` не зависит от `core`, а `core` не зависит от `markdown`;
- JPMS exports проверяют, что внутренние implementation packages не стали случайным SDK;
- девять golden-тем и pixel diff проходят без ослабления допусков;
- тесты ошибок LaTeX, unknown YAML, BOM/CRLF, symlink и atomic Save проходят;
- полный build, jlink и packaged smoke зелёные;
- Hello отсутствует в модуле, UI и smoke-ожиданиях.

**Ручная приёмка**

- открыть сложный Markdown с таблицей, формулой, изображением, callout, Front Matter, ссылками и transclusions;
- проверить Properties Apply, Notes Edit/Add, citations completion, Book и Branch Statistics;
- проверить, что функции Markdown доступны без активации отдельных плагинов;
- убедиться, что Settings → Plugins по-прежнему управляет внешними JAR.

**Готово, когда:** вся прежняя Markdown-функциональность работает из одного модуля, а старые обязательные Markdown-плагины удалены.

### Шаг 7. Убрать Markdown и Obsidian из `core` и `spi`

**Статус: завершён и проверен 2026-11-17.**

- Markdown API, Obsidian-навигация, fallback editor, Book, Branch Statistics и создание заметок перенесены в `markdown`.
  Platform-neutral Project/View/Index-контракты находятся в `spi`, Swing Book-контракт — в `spi-swing`; production
  `markdown → core` отсутствует. `ProjectAccess` отсекает устаревшие async-операции через `revision()`.
- Общий SQLite/Lucene-индекс работает с `IndexedDocument` и `IndexContributorRegistry`: путь, title, metadata, content,
  searchContent и произвольные attributes не зависят от Markdown. Схема SQLite обновлена до `user_version=2`; старый кэш
  удаляется и пересобирается из исходных файлов. Markdown title/Front Matter/body/tags/draft извлекает только
  `MarkdownIndexContributor`. Binary-файлы индексируются по имени/пути, а invalid UTF-8 поддерживаемого формата пропускается.
- `PlatformLanguagePurityTest` проверяет production Java и resources платформенных модулей;
  `NoForbiddenModuleDependenciesTest` одновременно проверяет JPMS и Gradle dependencies. Полный `clean build :app:devHome
  :app:jlink`: 542 теста, 0 failures/errors, 1 прежний `@Disabled`; девять golden-тем прошли без обновления эталонов.
  Standalone Template, `:app:run --args="--smoke"`, bundled/external packaged smoke успешны. Patch независимо применён
  к принятому шагу 6 + hotfix через Git и GNU patch; полная сборка и smoke повторены в применённой копии.
- Restart запускает новый процесс с прежними module/classpath и JVM arguments, ждёт завершение прежнего PID и не зависит
  от консоли Gradle/IDE. stdout/stderr нового процесса добавляются в `<home>/logs/restart.log`. Реальный process test и два
  последовательных packaged UI restart подтвердили disable → restart → enable → restart внешнего Template JAR с повторной
  активацией его tool window.
- Packaged UI-приёмка подтвердила Ctrl+Click по секционной ссылке, секционную трансклюзию, таблицу и формулу, два
  последовательных File → Save, поиск сохранённого текста, Book с двумя главами, Export Node и Branch Statistics.
  В `BookViewEditor.saveAsync()` future теперь завершается только после обновления modified/save-state.

**Изменения**

1. Перенести из `spi` MarkdownParser, BlockSpan, AstIndex, BlockKind, ObsidianLinkService и Markdown renderer extensions в `markdown.api` или внутренние пакеты `markdown`.
2. Перенести из `core.editor` ObsidianSyntax, Resolver, Navigator, ImagePreview, EditorSupport и CoreObsidianLinkService в `markdown`.
3. Перенести Markdown-специфичные BookViewEditor и BranchStatistics в `markdown`, оставив универсальные виртуальные виды и открытие файлов в `core`.
4. Ввести минимальные универсальные контракты, необходимые уже существующим сценариям:
   - `FileType`/file name или extension mapping;
   - языковой вклад в подсветку и редактор;
   - `IndexContributor` для заголовков, тегов, алиасов и Front Matter;
   - универсальный доступ к текущему проекту, путям и открытию файла.
5. Общий индекс продолжает индексировать имена файлов и текст; языковые атрибуты поставляет `markdown` через контракт.
6. Проверки выхода за Project, symlink target/parents и запрет перезаписи сохранить в соответствующих универсальных файловых сервисах или Markdown-адаптерах без ослабления.

**Автоматические проверки**

- `PlatformLanguagePurityTest`: production `spi`, `spi-swing`, `editor`, `metadata` и `core` не содержат идентификаторов `markdown`, `obsidian`, `frontmatter`;
- `NoForbiddenModuleDependenciesTest`: платформа не зависит от `markdown`;
- все Obsidian suites, resolver/navigator, image preview, comments, transclusions и citations проходят;
- Book Save/Export, Book restore filtering и Branch Statistics tests проходят;
- индекс после полного rebuild содержит прежние теги, алиасы, заголовки и поля;
- security tests на выход за Project, symlink и `CREATE_NEW` проходят;
- полный build и packaged smoke зелёные.

**Ручная приёмка**

- Ctrl+Click по Wikilink, открытие embed, Save полной и секционной трансклюзии;
- последовательный Save двух трансклюзий сохраняет обе независимые правки;
- поиск по тегам/тексту/виду, Book Export без перезаписи и статистика ветки работают как раньше.

**Готово, когда:** платформа не знает Markdown/Obsidian, но весь прежний Markdown-сценарий работает через расширения.

### Шаг 8. Создать `metadata` как описание, а не реализацию

**Статус: завершён и проверен 2026-11-18.**

- Создан JDK-only JPMS-модуль `ru.mcs.metadata`: собственный строгий JSON-parser, версионированный формат
  `formatVersion=1`, typed model и глобальная валидация actions, menus, toolbars, status bar, settings pages, keymap
  и panels. Проверяются уникальность ID, field IDs и option values, ссылки на commands/anchors, positions, field types,
  targets и capability syntax. `TargetRule` поддерживает `common`, `desktop`, `android`, `ios` и required/excluded
  capabilities без создания мобильных рендереров.
- `editor`, `core` и `markdown` поставляют собственные `META-INF/manuscriptium/*.json` через `MetadataProvider`.
  JPMS `provides`/`uses` и `META-INF/services` обеспечивают одинаковую загрузку в named runtime image и classpath-тестах.
  Реализации `AnAction` остаются в Java; Markdown добавляет вклады в core-группы через `parents`, не создавая зависимость
  `markdown → core`. Именованные separators адресуются независимо.
- `DesktopMetadata` в `core` отображает menus, toolbars, keymap и metadata-driven General settings. Incremental binder
  повторяет проходы до неподвижной точки внутри одного `bind()`: `before`/`after` ждут anchor и не деградируют в `last`;
  повторные вызовы не создают дубликатов. Общая страница настроек создаёт прежнюю Swing-форму и сохраняет состояние
  в `AppSettings`; `editor.json` описывает Undo/Redo и clipboard shortcuts. Для packaged `ResourceBundle` открыт только
  `ru.mcs.core.i18n`.
- Полный `clean build :app:devHome :app:jlink`: 552 теста, 0 failures/errors, 1 прежний перенесённый `@Disabled`;
  `ru.mcs.metadata` присутствует в runtime image. Девять Linux golden PNG побайтово не изменились и прошли в общем
  прогоне. Standalone `plugin-template clean build`, `:app:run --args="--smoke"`, bundled/external packaged smoke
  успешны: `failures={}`, `builtInFailures={}`, прежние editor providers и Markdown tool windows сохранены.
- Packaged UI-приёмка подтвердила File/View, separators, EditorTabPopup, Paragraph/Format/Insert, Command Palette,
  Notes/Properties, Markdown Source, Undo `Ctrl+Z`, Redo `Ctrl+Shift+Z`, Cut/Copy/Paste, General Reset/Apply,
  Light/Dark и два реальных restart с сохранением ru/en. `Ctrl+Y` в Typora-профиле по-прежнему удаляет строку.
- Patch независимо применён к принятому шагу 7 через Git и GNU patch; создание текстовых файлов и отсутствие вложенных
  корней проверены. Source tree применённой копии совпал с рабочим, полная сборка и smoke повторены. Коммит не создавался.

**Изменения**

1. Создать JDK-only модуль `metadata` с версионированным JSON-форматом описаний.
2. Определить данные для actions, menus, toolbars, status bar, settings pages, keymap, panels и named command IDs.
3. Добавить условия `targets`/`capabilities`, например `common`, `desktop`, `android`, `ios`. Это только правила применимости описаний.
4. Добавить загрузку и валидацию: версия, уникальность ID, известные типы полей, ссылки на command IDs, допустимость target/capability.
5. Не помещать в `metadata` Swing/Compose-компоненты, реализацию команд, бизнес-логику, выражения публикации или исполнитель ViewModel.
6. В `core` реализовать десктопное чтение описаний и построение Swing-интерфейса. Android/iOS-рендереры не создавать.
7. Первый сквозной срез: одна существующая страница настроек и одна группа меню/действий описываются метаданными; остальные части переводятся отдельными малыми срезами.
8. Модули `editor` и `markdown` поставляют собственные описания как ресурсы; реализации команд остаются в соответствующих модулях.

**Автоматические проверки**

- `MetadataPurityTest`: отсутствуют зависимости на AWT, Swing, Android, Compose, `core`, `editor` и `markdown`;
- parser/validator tests: валидное описание, дубликат ID, неизвестный target, отсутствующий command ID, неподдерживаемая версия;
- target selection tests: common/desktop/android/ios выбираются декларативно без реализации мобильных клиентов;
- Swing form tests: описанная страница создаёт прежние значения, Apply/Reset/OK и сохраняет тот же state;
- action/keymap tests: прежние меню, ПКМ, палитра и shortcuts работают;
- полный build и packaged smoke зелёные.

**Ручная приёмка**

- страница настроек визуально и функционально эквивалентна прежней;
- Light/Dark, ru/en, shortcuts и меню не потеряли элементы;
- изменение настройки по-прежнему применяется и сохраняется после перезапуска.

**Готово, когда:** метаданные описывают реальную часть приложения, desktop отображает её, а модуль описаний не содержит исполняемой прикладной логики.

### Шаг 9. Собрать `app` как продукт и доказать независимость платформы

**Статус: завершён и проверен 2026-11-18.**

- Основной `app` по-прежнему явно собирает `core`, `editor` и `markdown`. Добавлен тестовый source set
  `app/src/minimalSmoke` с самостоятельным JPMS descriptor того же `ru.mcs.app`: в его runtime не попадают основной
  output `app` и `markdown`. `:app:check` зависит от `:app:minimalSmoke`; отдельный `product-minimal` не создавался.
- `CoreBootstrap.createHeadlessEditorManager()` возвращает публичный `FileEditorManager`, не экспортируя implementation
  package `core.editor`. `CoreServices` и минимальная компоновка используют общий `ServiceLoader`-цикл. Dependency assertions
  проверяют отсутствие `requires ru.mcs.markdown` у `spi`, `spi-swing`, `metadata`, `editor` и `core`, отсутствие языкового
  модуля в boot layer и единственный provider `editor.text`.
- `minimalSmoke` открывает `plain.txt`, выполняет insert, undo, redo и завершённый Save; содержимое на диске равно
  `alpha\nbeta\n`. Для headless-проверки `EditorCanvas` не создаёт native `DropTarget`, а в обычном UI XDND сохраняется.
- Пустой `EditorTabs` содержит New File, New Project, Open Project и Open File, вызывающие зарегистрированные actions.
  New File использует `Files.createFile`/`CREATE_NEW`; New Project использует `Files.createDirectories`. Тесты подтверждают
  запрет перезаписи существующего файла и замены regular file каталогом. Базовые сервисы инициализируются без Project
  и built-in модулей.
- Полный `clean build :app:devHome :app:jlink :app:minimalSmoke`: 557 тестов, 0 failures/errors, 1 прежний
  `MarkdownFileEditorTest.loadsOutsideEdtAndSwitchesModesInsideSameComponent` остаётся `@Disabled`. Runtime image содержит
  `app`, `core`, `editor`, `markdown`, `metadata`, `spi` и `spi-swing`; девять Linux golden PNG побайтово не изменились.
  `:app:run --args="--smoke"`, standalone `plugin-template clean build`, bundled/external packaged smoke успешны:
  `failures={}`, `builtInFailures={}`, полный список editor providers и Markdown tool windows сохранён.
- Packaged UI-приёмка чистого home подтвердила четыре quick-start кнопки, создание и открытие `Quick Start.md`, ввод,
  Undo/Redo, File → Save с проверкой содержимого на диске и Canvas-заголовок. Создан вложенный Project и
  `inbox/deep/Note.md`; Open File и Open Project выполнены через центральную панель. Ошибок UI в журнале нет.

**Изменения**

1. `app` явно собирает Manuscriptium из `core`, `editor`, `markdown` и метаданных продукта.
2. Пустой запуск остаётся простым: пользователь может создать Markdown, открыть отдельный файл, создать папку или открыть Project.
3. Платформа не требует Project или Markdown для инициализации базовых сервисов.
4. Добавить `minimalSmoke` как тестовый source set/Gradle task внутри `app`, без создания отдельного модуля `product-minimal`.
5. `minimalSmoke` запускает состав `core + editor` без `markdown`, открывает и сохраняет обычный текстовый файл.
6. Не создавать AI/Zig-продукты; проверка ограничивается доказательством независимости платформы.

**Автоматические проверки**

- `:app:minimalSmoke` стартует без `markdown` в runtime module/path и печатает явный success marker;
- smoke открывает и сохраняет `.txt`, undo/redo работает;
- Manuscriptium packaged smoke активирует полный продукт и печатает success marker;
- dependency test подтверждает, что добавление/исключение `markdown` меняет только сборку `app`;
- полный build, devHome и jlink зелёные.

**Ручная приёмка**

- Manuscriptium при пустом состоянии позволяет немедленно начать заметку;
- Open File, Open Project и New Project/Folder не требуют лишней настройки;
- minimal smoke не показывает Markdown-меню, Markdown-панели и Markdown file types.

**Готово, когда:** платформа стартует без Markdown, а Manuscriptium с Markdown сохраняет прежний простой пользовательский сценарий.

### Шаг 10. Обновить SDK и проверить пользовательские расширения

**Статус: завершён и проверен 2026-11-19.**

- `McsSdk.VERSION` обновлён до `0.10.0`, внешние манифесты используют `sdk-version: "0.10"`. Прежняя линия `0.9`
  несовместима и требует пересборки JAR; искусственная бинарная совместимость не сохранялась.
- `plugin-template` остаётся отдельным standalone Gradle-проектом и теперь собирает два независимых JAR: универсальный
  `manuscriptium-plugin-template-0.1.0.jar` через `spi-swing` и `manuscriptium-markdown-extension-0.1.0.jar` через
  публичный API `markdown`. Оба JAR содержат собственные JPMS descriptors и манифесты, но не классы SDK или продукта.
  Сборка повторена из физически отдельного каталога с `includeBuild` на исходное дерево.
- `PluginOrdering` и `PluginManager` получают IDs фактически запущенных built-in модулей. Такой модуль удовлетворяет
  `depends-on`, не активируется как внешний JAR и не получает отдельный parent layer. Внешний descriptor не может занять
  ID запущенного built-in. `markdown` остаётся встроенным модулем, а `MarkdownParser` предоставляется через `ServiceRegistry`.
- В packaged smoke добавлен `--smoke-actions=id1,id2`, реально выполняющий указанные действия после активации плагинов.
  Оба внешних JAR загружены и выполнили `template.hello` и `markdown.template.inspect`; Markdown action получил
  `FlexmarkMarkdownParser`, разобрал образец в 10 spans с одним heading, `failures={}` и `builtInFailures={}`.
- Invalid packaged smoke подтвердил `old.plugin=incompatible SDK: required 0.9, application 0.10.0` и
  `broken.consumer=missing dependency (absent, disabled or incompatible): absent.provider`; процесс ожидаемо завершился
  с ненулевым кодом. Тест `PluginInstallerTest` подтвердил восстановление прежней установки после неудачной замены.
- Полный `build :app:devHome :app:jlink :app:minimalSmoke`: 563 теста, 0 failures/errors, 1 прежний
  `MarkdownFileEditorTest.loadsOutsideEdtAndSwitchesModesInsideSameComponent` остаётся `@Disabled`. Девять Linux golden PNG
  побайтово совпадают с шагом 9. Standalone build, packaged external smoke и обязательные negative smoke проверки зелёные.
- Ручная packaged UI-приёмка подтвердила установку обоих JAR с явным предупреждением полного JVM access и отдельным
  согласием, действия и tool window, disable/enable с restart, pending update `0.1.0` → `0.2.0` только после restart,
  отображение несовместимого JAR без остановки остальных расширений. Сетевой репозиторий плагинов не создавался.

**Изменения**

1. Обновить `plugin-template`, `docs/plugin-sdk.md`, манифесты и примеры зависимостей.
2. Задокументировать два типа внешних расширений:
   - универсальное расширение платформы через `spi`/`spi-swing`;
   - расширение формата через публичный API `markdown` или будущего языкового модуля.
3. Обновить SDK version по фактическому изменению контрактов, не сохраняя искусственную бинарную совместимость.
4. Проверить staging/rollback, pending update, зависимости и предупреждение полного JVM access.
5. Не создавать сетевой репозиторий плагинов.

**Автоматические проверки**

- standalone plugin-template собирается отдельно от основного дерева;
- внешний тестовый JAR устанавливается, загружается и выполняет действие в packaged app;
- зависимость расширения от `markdown` разрешается корректно;
- incompatible SDK/missing dependency дают понятную ошибку;
- disable/enable с перезапуском и rollback не повреждают прежнюю установку;
- полный build и оба smoke зелёные.

**Ручная приёмка**

- установить внешний JAR, проверить действие, выключить/включить его и перезапустить приложение;
- проверить обновление и ошибку несовместимого JAR;
- убедиться, что предупреждение безопасности остаётся явным.

**Готово, когда:** пользовательские расширения работают на новой структуре, хотя старые JAR требуют пересборки.

### Шаг 11. Упростить документацию и репозиторий

**Статус: завершён и проверен 2026-11-19.**

- Удалены прежние `plan.md`, `REQUIREMENTS.md`, `TYPORA_FEATURES.md`, `EDITOR_CANVAS_PLAN.md`, оба варианта
  `Manuscriptum.md`, устаревший `prompt.md` и три промежуточных root patch-файла. Актуальным маршрутом остаётся
  `plan_refactor.md`; сетевые и пользовательские данные не затрагивались.
- README теперь начинается с таблицы семи модулей и самодостаточных команд сборки/запуска. Исторические E0–E8-метки
  и ссылки на удалённые планы убраны; Project, SDK 0.10, внешние JAR, отсутствие Markdown в платформе и лицензии
  описаны в действующих руководствах. Отдельный architecture document не потребовался: ограничения уже находятся
  в README и `docs/plugin-sdk.md`.
- Добавлены MIT `LICENSE` и `THIRD_PARTY_NOTICES.md`. Проверены локальные Lucide ISC/Feather MIT notices и
  test-only Liberation Sans GPL v2 с font exception. Standalone template получил Lucide LICENSE рядом с SVG.
  Девять встроенных CSS-тем являются собственными ресурсами проекта; их содержимое и golden PNG не изменялись.
- `RepositoryDocumentationTest` автоматически проверяет актуальный набор документов, отсутствие прежних планов и
  root patches, локальные Markdown-ссылки и отсутствие ссылок на старую модульную структуру в действующих руководствах.
- Полный `clean build :app:devHome :app:jlink :app:minimalSmoke`: 566 тестов, 0 failures/errors, 1 прежний
  `MarkdownFileEditorTest.loadsOutsideEdtAndSwitchesModesInsideSameComponent` остаётся `@Disabled`. Standalone build,
  Gradle-run smoke и packaged external smoke с обоими действиями успешны; девять Linux golden PNG не изменились.

**Изменения**

1. Оставить `plan_refactor.md` как актуальный план рефакторинга и отражать в нём только проверенные результаты.
2. Обновить README: модули, запуск, Project, сборка, внешние плагины и отсутствие Markdown в платформе.
3. Перенести ещё актуальные архитектурные ограничения в README/SDK или отдельный короткий architecture document.
4. После финальной приёмки удалить прежние плановые документы: старый `plan.md`, `REQUIREMENTS.md`, `TYPORA_FEATURES.md`, `EDITOR_CANVAS_PLAN.md`, `Manuscriptum.md` и промежуточные `Plan3*`, если они присутствуют в репозитории.
5. Удалить patch-файлы из корня репозитория и временные smoke-артефакты из исходного дерева.
6. Удалить упоминания PSI из плановой терминологии нашего проекта; SPI остаётся названием модуля расширений. Сравнения с IntelliJ PSI в исторической документации не переносить.
7. Добавить MIT LICENSE, проверить лицензии ресурсов и встроенных тем без изменения их содержимого в этом рефакторинге.

**Автоматические проверки**

- documentation link check не находит ссылок на удалённые документы;
- поиск по репозиторию не находит устаревших инструкций `Vault`, старых модулей и Hello, кроме явной миграционной записи;
- полный build после удаления документов и ресурсов остаётся зелёным.

**Ручная приёмка**

- по README можно собрать, запустить и проверить приложение без старых документов;
- SDK объясняет создание внешнего расширения без обращения к удалённым планам.

**Готово, когда:** в репозитории остаётся один непротиворечивый актуальный маршрут и актуальные руководства.

### Шаг 12. Выполнить финальную приёмку без потери функциональности

**Статус: завершён и проверен локально 2026-11-19; удалённый CI на трёх ОС не запускался.**

#### Реестр переноса тестов

Сверено сохранённое дерево перед переносами: 136 suites, включая четыре архитектурных suite, добавленных на шагах 2–3 после исходной базы шага 1 (132 suites / 502 теста). Из них 69 перенесены или переименованы, 65 остались на прежних путях, удалены только два согласованных suite Hello. Каждый перенос перечислен ниже; имена методов у двух переименованных suites сохранены.

| Исходный suite | Итоговое расположение |
|---|---|
| `core/src/test/java/ru/mcs/core/editor/CoreObsidianLinkServiceTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/DefaultObsidianLinkServiceTest.java` |
| `core/src/test/java/ru/mcs/core/editor/ObsidianEditorSupportTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/ObsidianEditorSupportTest.java` |
| `core/src/test/java/ru/mcs/core/editor/ObsidianImagePreviewTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/ObsidianImagePreviewTest.java` |
| `core/src/test/java/ru/mcs/core/editor/ObsidianLinkNavigatorTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/ObsidianLinkNavigatorTest.java` |
| `core/src/test/java/ru/mcs/core/editor/ObsidianLinkResolverTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/ObsidianLinkResolverTest.java` |
| `core/src/test/java/ru/mcs/core/editor/ObsidianSyntaxTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/ObsidianSyntaxTest.java` |
| `core/src/test/java/ru/mcs/core/editor/TextFileEditorProviderTest.java` | `editor/src/test/java/ru/mcs/editor/text/TextFileEditorProviderTest.java` |
| `core/src/test/java/ru/mcs/core/project/BookViewDocumentTest.java` | `markdown/src/test/java/ru/mcs/markdown/project/BookViewDocumentTest.java` |
| `core/src/test/java/ru/mcs/core/project/BookViewEditorTest.java` | `markdown/src/test/java/ru/mcs/markdown/project/BookViewEditorTest.java` |
| `core/src/test/java/ru/mcs/core/project/BranchStatisticsTest.java` | `markdown/src/test/java/ru/mcs/markdown/project/BranchStatisticsTest.java` |
| `core/src/test/java/ru/mcs/core/project/MarkdownNoteCreatorTest.java` | `markdown/src/test/java/ru/mcs/markdown/project/MarkdownNoteCreatorTest.java` |
| `plugins/editor-styles/src/test/java/ru/mcs/plugin/editorstyle/CssValuesTest.java` | `markdown/src/test/java/ru/mcs/markdown/style/CssValuesTest.java` |
| `plugins/editor-styles/src/test/java/ru/mcs/plugin/editorstyle/EditorStyleServiceTest.java` | `markdown/src/test/java/ru/mcs/markdown/style/EditorStyleServiceTest.java` |
| `plugins/editor-styles/src/test/java/ru/mcs/plugin/editorstyle/FontRegistryTest.java` | `markdown/src/test/java/ru/mcs/markdown/style/FontRegistryTest.java` |
| `plugins/editor-styles/src/test/java/ru/mcs/plugin/editorstyle/MarkdownGoldenTest.java` | `markdown/src/test/java/ru/mcs/markdown/style/MarkdownGoldenTest.java` |
| `plugins/editor-styles/src/test/java/ru/mcs/plugin/editorstyle/StyleResolverTest.java` | `markdown/src/test/java/ru/mcs/markdown/style/StyleResolverTest.java` |
| `plugins/editor-styles/src/test/java/ru/mcs/plugin/editorstyle/TyporaThemesTest.java` | `markdown/src/test/java/ru/mcs/markdown/style/TyporaThemesTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/BlockRendererTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/BlockRendererTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/CitationLayoutTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/CitationLayoutTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/EditorActionFactoryTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/EditorActionFactoryTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/EditorCanvasTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/EditorCanvasTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/EditorCommandsTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/EditorCommandsTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/MarkdownEditorConfigurableTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/MarkdownEditorConfigurableTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/MarkdownEditorStateTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/MarkdownEditorStateTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/MarkdownFileEditorProviderTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/MarkdownFileEditorProviderTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/MarkdownFileEditorTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/MarkdownFileEditorTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/MarkdownLayoutEngineTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/MarkdownLayoutEngineTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/MarkdownParseCoordinatorTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/MarkdownParseCoordinatorTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/MessagesResourcesTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/MessagesResourcesTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/action/EditorContextMenuBuilderTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/action/EditorContextMenuBuilderTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/action/SelectionExpanderTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/action/SelectionExpanderTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/action/TyporaKeymapTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/action/TyporaKeymapTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/action/WordCountTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/action/WordCountTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/architecture/EditorPackageDependenciesTest.java` | `markdown/src/test/java/ru/mcs/markdown/architecture/EditorPackageDependenciesTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/layout/CrossReferenceWidgetLayoutTest.java` | `markdown/src/test/java/ru/mcs/markdown/layout/CrossReferenceWidgetLayoutTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/layout/MarkdownWidgetLayoutTest.java` | `markdown/src/test/java/ru/mcs/markdown/layout/MarkdownWidgetLayoutTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/mode/FocusModeTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/mode/FocusModeTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/mode/TypewriterModeTest.java` | `editor/src/test/java/ru/mcs/editor/mode/TypewriterModeTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/model/DocumentModelImplTest.java` | `editor/src/test/java/ru/mcs/editor/model/DocumentModelImplTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/model/MarkdownDocumentCodecTest.java` | `editor/src/test/java/ru/mcs/editor/model/TextDocumentCodecTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/model/TextBufferPerformanceTest.java` | `editor/src/test/java/ru/mcs/editor/model/TextBufferPerformanceTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/model/TextBufferTest.java` | `editor/src/test/java/ru/mcs/editor/model/TextBufferTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/reference/CitationIndexTest.java` | `markdown/src/test/java/ru/mcs/markdown/reference/CitationIndexTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/reference/CrossReferenceIndexTest.java` | `markdown/src/test/java/ru/mcs/markdown/reference/CrossReferenceIndexTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/view/AutoPairTest.java` | `editor/src/test/java/ru/mcs/editor/view/AutoPairTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/view/DocumentLineIndexTest.java` | `editor/src/test/java/ru/mcs/editor/view/DocumentLineIndexTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/view/EditorCanvasCitationTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/view/EditorCanvasCitationTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/view/EditorCanvasInteractionTest.java` | `editor/src/test/java/ru/mcs/editor/view/EditorCanvasInteractionTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/view/EditorCanvasMarkdownTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/view/EditorCanvasMarkdownTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/view/EditorCanvasTableTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/view/EditorCanvasTableTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/view/ImageInputServiceTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/view/ImageInputServiceTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/view/ObsidianCommentsTest.java` | `markdown/src/test/java/ru/mcs/markdown/editor/view/ObsidianCommentsTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/view/TextLayoutEngineTest.java` | `editor/src/test/java/ru/mcs/editor/view/TextLayoutEngineTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/widget/ImageCacheTest.java` | `markdown/src/test/java/ru/mcs/markdown/widget/ImageCacheTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/widget/ImageSourceTest.java` | `markdown/src/test/java/ru/mcs/markdown/widget/ImageSourceTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/widget/TableModelTest.java` | `markdown/src/test/java/ru/mcs/markdown/widget/TableModelTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/widget/TransclusionConcurrencyTest.java` | `markdown/src/test/java/ru/mcs/markdown/widget/TransclusionConcurrencyTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/widget/TransclusionDocumentTest.java` | `markdown/src/test/java/ru/mcs/markdown/widget/TransclusionDocumentTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/widget/TransclusionSessionTest.java` | `markdown/src/test/java/ru/mcs/markdown/widget/TransclusionSessionTest.java` |
| `plugins/markdown-editor/src/test/java/ru/mcs/plugin/mdeditor/widget/WidgetRegistryTest.java` | `markdown/src/test/java/ru/mcs/markdown/widget/WidgetRegistryTest.java` |
| `plugins/markdown-parser/src/test/java/ru/mcs/plugin/mdparser/FlexmarkMarkdownParserTest.java` | `markdown/src/test/java/ru/mcs/markdown/parser/FlexmarkMarkdownParserTest.java` |
| `plugins/markdown-parser/src/test/java/ru/mcs/plugin/mdparser/MarkdownCorpusTest.java` | `markdown/src/test/java/ru/mcs/markdown/parser/MarkdownCorpusTest.java` |
| `plugins/math-renderer/src/test/java/ru/mcs/plugin/math/JLaTeXMathRenderingServiceTest.java` | `markdown/src/test/java/ru/mcs/markdown/math/JLaTeXMathRenderingServiceTest.java` |
| `plugins/notes/src/test/java/ru/mcs/plugin/notes/MarkdownFilesTest.java` | `markdown/src/test/java/ru/mcs/markdown/notes/MarkdownFilesTest.java` |
| `plugins/notes/src/test/java/ru/mcs/plugin/notes/NoteStatisticsTest.java` | `markdown/src/test/java/ru/mcs/markdown/notes/NoteStatisticsTest.java` |
| `plugins/notes/src/test/java/ru/mcs/plugin/notes/OpenNotesPanelTest.java` | `markdown/src/test/java/ru/mcs/markdown/notes/OpenNotesPanelTest.java` |
| `plugins/properties/src/test/java/ru/mcs/plugin/properties/FrontMatterTest.java` | `markdown/src/test/java/ru/mcs/markdown/frontmatter/FrontMatterTest.java` |
| `plugins/properties/src/test/java/ru/mcs/plugin/properties/MarkdownFileStoreTest.java` | `markdown/src/test/java/ru/mcs/markdown/frontmatter/MarkdownFileStoreTest.java` |
| `plugins/properties/src/test/java/ru/mcs/plugin/properties/PropertiesPanelTest.java` | `markdown/src/test/java/ru/mcs/markdown/properties/PropertiesPanelTest.java` |

| Согласованно удалённый suite | Замена / основание |
|---|---|
| `plugins/hello/src/test/java/ru/mcs/plugin/hello/HelloConfigurableTest.java` | Замена не требуется: удалён учебный Hello configurable вместе с согласованным `plugins:hello`. |
| `plugins/hello/src/test/java/ru/mcs/plugin/hello/HelloStateTest.java` | Замена не требуется: удалено учебное состояние Hello; функции продукта этот suite не проверял. |

`CoreObsidianLinkServiceTest` переименован в `DefaultObsidianLinkServiceTest`, а `MarkdownDocumentCodecTest` — в `TextDocumentCodecTest`; наборы их тестовых методов совпадают. Оба исходных `MessagesResourcesTest` учтены раздельно: core-версия осталась в `core`, версия Markdown-редактора перенесена в `markdown`. Текущее дерево содержит 154 suites; добавленные после базы проверки не заменяют собой исходные переносы.

#### Фактические результаты приёмки

- Чистый прогон `clean test :app:devHome :app:jlink :app:minimalSmoke` дал 569 тестов в 154 XML suites,
  0 failures/errors и 1 skipped. Единственный пропущенный тест — прежний
  `MarkdownFileEditorTest.loadsOutsideEdtAndSwitchesModesInsideSameComponent`, который остаётся `@Disabled`.
  После внешнего тайм-аута на packaging успешно повторены `:app:jlink :app:minimalSmoke`; minimal image содержит
  только `ru.mcs.core` и `ru.mcs.editor`, не содержит Markdown и подтверждает `editor.text`, undo/redo и Save.
- Все девять Linux golden PNG побайтово совпадают с шагом 11. Standalone `plugin-template clean build` успешен;
  оба JAR собраны с `sdk-version: "0.10"`. Packaged built-in и external smoke завершились с пустыми
  `failures`/`builtInFailures`; `template.hello` и `markdown.template.inspect` реально выполнены.
  Несовместимый SDK `0.9` и отсутствующая зависимость `absent.plugin` ожидаемо дали ненулевой выход и точные ошибки.
- В packaged UI повторены Project и восстановление вкладок, поиск, Classification, Clone/Move Link,
  Branch Statistics, Properties Apply, Book Save Chapters и Export без перезаписи; Markdown Canvas/Source,
  Front Matter, comments, heading, bold, Wikilink, citation, callout, table, math, image и bibliography;
  Notes, Undo/Redo, Save, Focus/Typewriter, Light/Dark, editor theme `github → night`, ru/en, Settings и restart
  с восстановлением Project/вкладки. Внешний JAR установлен через Settings → Plugins после явного предупреждения
  о полном доступе к JVM; без подтверждения установка запрещена, после restart доступны toolbar, tool window
  и реально выполненное действие `template.hello`.
- Приёмка выявила и закрыла три дефекта переноса: повторные одинаковые embed теперь имеют независимые Swing
  components благодаря occurrence offset в `TransclusionKey`, а rebuild того же offset переиспользует component;
  Margin Note при caret в Front Matter вставляется после closing delimiter без потери пользовательских полей;
  `core.file.save` и `core.file.saveAll` получили declarative OS shortcuts и общий action update-контракт.
  Section Save трансклюзии по-прежнему перечитывает latest target и заменяет текущую секцию, а не выполняет
  трёхстороннее слияние двух разных вариантов одной секции.
- Исчезли только согласованные элементы: отдельные built-in Markdown-плагины, Hello вместе с двумя его suites,
  старая бинарная совместимость JAR и прежние документы. `HelloStateTest` не восстанавливался. Удалённый CI
  Ubuntu/Windows/macOS не запускался, поэтому локальная Linux-приёмка не выдаётся за результаты трёх ОС.

**Изменения**

1. Сверить итоговый реестр тестов и пользовательских функций с базовым из шага 1.
2. Для каждого перенесённого теста указать новое расположение; для каждого удалённого теста — эквивалентную замену.
3. Проверить, что исчезли только согласованные элементы: отдельные встроенные Markdown-плагины, Hello, старые JAR compatibility и прежние документы.
4. Зафиксировать результат без создания коммита за пользователя.

**Полная автоматическая приёмка**

```bash
./gradlew clean test :app:devHome :app:jlink :app:minimalSmoke
```

Дополнительно:

- все unit/integration/Swing tests без failures/errors;
- все девять Linux golden-тем без обновления эталонов;
- packaged Manuscriptium smoke;
- external plugin smoke;
- structural tests на JPMS/Gradle dependencies, SPI purity и отсутствие Markdown в платформе;
- CI Ubuntu/Windows/macOS зелёный после исправления smoke-ожиданий;
- повторное открытие существующего проекта не меняет пользовательские Markdown и views без команды пользователя.

**Финальный ручной сценарий**

1. Открыть существующий Project и проверить дерево, поиск, вид, клон и восстановление вкладок.
2. Создать Markdown, ввести текст, проверить Enter, списки, выделение, clipboard, undo/redo и Save.
3. Переключить Source/Canvas, Focus и Typewriter; проверить каретку и прокрутку.
4. Проверить таблицу, изображение, формулу, callout, Front Matter и Properties.
5. Проверить Wikilink, две трансклюзии с независимым Save, comments/Notes, cross references и citations.
6. Проверить Book Save Chapters, повторный Export без перезаписи и Branch Statistics.
7. Проверить девять тем выборочно в UI, Light/Dark, ru/en, keymap и настройки.
8. Проверить внешний JAR-плагин и minimal smoke без Markdown.

**Готово, когда:** все существующие пользовательские возможности подтверждены на новой структуре, а единственные отличия перечислены в разделе «Сознательные изменения».

## 4. Порядок остановки и исправления

Если после любого шага обнаружена потеря поведения:

1. не переходить к следующему шагу;
2. добавить или восстановить регрессионный тест;
3. исправить перенос, а не удалять старую проверку;
4. повторить полный build и соответствующий пользовательский сценарий;
5. обновить статус плана только после фактически зелёной проверки.

Если выясняется, что перенос требует новой универсальной абстракции, сначала добавляется минимальный контракт для уже существующего сценария. Не создаются общие VFS, PSI, expression engine или мобильные API «на будущее».

## 5. Критерий завершения всего рефакторинга

Рефакторинг завершён, когда одновременно выполнены условия:

- физическая структура соответствует семи целевым модулям;
- `metadata` содержит описания, но не отображение и не реализацию команд;
- `editor` редактирует обычный текст без Markdown;
- `markdown` содержит всю прежнюю встроенную Markdown-функциональность и предоставляет API для расширений;
- `core` содержит Project и платформенную реализацию, но не знает о Markdown;
- `app` собирает Manuscriptium, а `minimalSmoke` доказывает запуск без Markdown;
- все существующие функции и защитные инварианты подтверждены тестами и ручной приёмкой;
- старые планы, Hello и обязательные Markdown-плагины удалены;
- внешние расширения работают после обновления к новому SDK;
- сборка, тесты, golden и smoke зелёные на всех целевых ОС.
