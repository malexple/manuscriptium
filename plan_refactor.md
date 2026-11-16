# Manuscriptium — план архитектурного рефакторинга

Дата: 2026-11-16. Статус: шаги 1–4 завершены; следующий шаг — выделение универсального `editor`.

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
| `editor` | `spi`, `spi-swing`, `java.desktop`; без `core` и `markdown`. |
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
