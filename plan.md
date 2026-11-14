# Manuscriptium — план реализации (plan.md)

Версия документа: 1.6 (2026-11-14). Статус: Фазы 0–4 и Canvas-редактор E0–E4 завершены; следующий этап — E5, публикация Фазы 5 продолжится после E9.

## 1. Цель и приоритеты

Manuscriptium — кроссплатформенное (Windows, Linux, macOS) десктопное приложение для заметок и блог-публикаций на Java 21 + Swing + FlatLaf.
Идея: «Trilium из коробки + файлы Markdown как в Obsidian + расширяемая оболочка как IntelliJ IDEA». Пользователь не должен настраивать 30 плагинов вручную.

**Приоритет №1 — расширяемая оболочка (UI-платформа) и иерархия. Затем функциональность. Затем сторонние плагины.**

Принципы:
- Ядро «глупое»: оболочка ничего не знает о заметках; всё прикладное — bundled-плагины, которые можно отключить.
- Evidence over assumptions: каждый рискованный кусок сначала прототип (Фаза 0).
- Не отказываться от идей из-за сложности — декомпозировать.

## 2. Технические решения (зафиксированы)

| Область | Решение |
|---|---|
| Язык / сборка | Java 21 (весь проект, включая SDK), Gradle Groovy DSL, toolchain 21 |
| UI | Swing + FlatLaf (+ flatlaf-extras для SVG) |
| Модульность | JPMS; один `ModuleLayer` на плагин; родитель — слой ядра/SPI |
| Модули Gradle | `spi`, `core`, `app`, `plugins/*` |
| Пакеты | `ru.mcs.spi`, `ru.mcs.core`, `ru.mcs.app`, `ru.mcs.plugin.<name>` |
| SDK (`spi`) | Без внешних зависимостей (только JDK + Swing). Semver, поле `sdk-version` в `plugin.yml` |
| Манифест плагина | `plugin.yml`: метаданные + декларативные вклады (действия, иконки, панели, настройки); i18n-ключи |
| Точка входа плагина | `provides ru.mcs.spi.Plugin with ...` в `module-info.java`; загрузка `ServiceLoader.load(layer, Plugin.class)` |
| Ленивая загрузка | Ядро знает вклады из `plugin.yml` до загрузки класса плагина |
| Безопасность | Capability-модель через `ApplicationContext`; `permissions` — декларация для пользователя; при установке предупреждение «плагин имеет полный доступ к JVM». SecurityManager не используется |
| Сервисы | `ServiceRegistry` двух уровней: application и vault; `PersistentStateComponent` регистрируется явно |
| Hot-reload | Тяжёлые Java-плагины — требуют перезапуска; лёгкие скрипты — на лету |
| Скрипты | Только собственный JS-подобный интерпретатор (из `wiremock-js-extension`), фасад `McsFacade`. Рантайм-компиляция Java — поздняя фаза |
| Редактор | Собственный Swing Canvas по `EDITOR_CANVAS_PLAN.md` и `TYPORA_FEATURES.md`; `FileEditorProvider` встраивает Canvas/Source в одну Markdown-вкладку. JavaFX/JCEF/WebView не используются |
| Docking | Собственный, на `JSplitPane`; стороны LEFT/RIGHT/BOTTOM; stripes; без drag-and-drop панелей и плавающих окон |
| Окна | Одно окно — один vault; несколько окон допустимы на уровне ядра |
| Вкладки | Закрепление + восстановление сессии в MVP; сплиты позже |
| Настройки | Глобальные: `%APPDATA%/Manuscriptium`, `~/.config/manuscriptium`, `~/Library/Application Support/Manuscriptium`. Vault: `.manuscriptium/`. Синхронизируется только vault |
| i18n | `ResourceBundle` ru/en с самого начала, ключи в SDK и манифесте |
| Иконки | Lucide (MIT), SVG; один SVG + `ColorFilter` для темы; загрузка через `Icons.load(Class, path)` (пакет с иконками должен быть открыт в `module-info`) |
| Темы | Light/Dark, токены FlatLaf (JSON/properties), переключение на лету, детектор темы ОС — отдельная задача |
| macOS | `apple.laf.useScreenMenuBar`, `apple.awt.application.name`, `apple.awt.application.appearance=system`; unified toolbar через `apple.awt.fullWindowContent` + `apple.awt.transparentTitleBar` (+ отступ ~70 px под кнопки окна) |
| Поставка | jlink/jpackage (плагин beryx); немодульные зависимости — merged-модуль; Win MSI/ZIP, Linux DEB, macOS DMG; без подписи на MVP |
| Распространение плагинов | Основной формат — JAR с `plugin.yml` в корне. Папка и ZIP остаются источниками локальной установки; сетевой каталог и сервис загрузки реализуются отдельным поздним этапом |
| Источники плагинов | Bundled — `<installation>/system-plugins/`; внешние — `<home>/plugins/` (`.manuscriptium/plugins/`). Единый реестр и граф; установка и обновления изменяют только внешний каталог |
| Тесты | JUnit 5 + AssertJ, headless-логика (ActionManager, резолвер слоёв, манифесты); без UI-тестов Swing |
| CI | GitHub/Forgejo Actions: Windows, Ubuntu, macOS — build, test, jlink |
| Лицензия | MIT |

## 3. Структура репозитория

```
manuscriptium/
  settings.gradle
  build.gradle              # общие настройки, toolchain 21
  spi/                      # SDK: интерфейсы, records, без внешних зависимостей
    src/main/java/module-info.java   # module ru.mcs.spi
  core/                     # оболочка: Actions, ToolWindows, Editor area, Settings, Theme, PluginManager
    src/main/java/module-info.java   # module ru.mcs.core
  app/                      # точка входа, jlink/jpackage, ассеты
    src/main/java/module-info.java   # module ru.mcs.app (mainClass ru.mcs.app.ManuscriptiumApp)
  plugins/
    demo-notes/  demo-properties/  demo-console/  demo-settings/
  docs/  plan.md  prompt.md
```

Зависимости: `app` → `core` → `spi`; плагины → `spi` (никогда не → `core`).
Внешние библиотеки (FlatLaf, SQLite, Lucene, Flexmark, SnakeYAML) подключаются в `core`/плагины, не в `spi`.

## 4. Фазы

### Фаза 0. Каркас и риски
1. Многомодульный Gradle (Groovy), toolchain 21, `module-info` у `spi`, `core`, `app`.
2. Плагин jlink применён только к `app`; немодульные зависимости — merged-модуль.
3. Прототип: jlink-образ запускается на Win/Linux/macOS; `PluginManager` читает `plugin.yml` из `.manuscriptium/plugins/<id>/`, создаёт `ModuleLayer`, грузит `Plugin` через `ServiceLoader.load(layer, ...)`; тестовый плагин регистрирует действие и панель.
4. CI на трёх ОС: build, test, jlink.
5. **Готово, когда:** образ запускается на трёх ОС, показывает окно FlatLaf с панелью из внешнего плагина.

### Фаза 1. Оболочка
- `ActionManager`: `AnAction` (id, текст, иконка, `update()`), `ActionGroup`, `Constraints` (FIRST/LAST/BEFORE/AFTER); меню/тулбары/контекстные меню строятся из групп.
- `ToolWindowManager`: stripes, показ/скрытие, ширина, сторона, сохранение раскладки.
- Editor area: вкладки, закрепление, `FileEditorProvider`, восстановление сессии.
- Статус-бар с виджетами, `MessageBus` (topics), `NotificationManager` (баллоны).
- `ServiceRegistry` (application / vault), `PersistentStateComponent`.
- Контрибуции из `plugin.yml` + ленивая загрузка.
- **Готово, когда:** оболочка с тремя демо-панелями и действиями; раскладка и вкладки переживают перезапуск.

### Фаза 2. Настройки, темы, i18n, палитра команд
- [x] Окно настроек: дерево (`ConfigurableProvider`), OK/Apply/Reset, `isModified`, вложенность через `getParentId`.
- [x] `McsThemeManager`: Light/Dark, на лету, токены JSON, детектор темы ОС, перезагрузка файла темы при правке.
- [x] `Icons.load` + `ColorFilter`, набор Lucide.
- [x] `ResourceBundle` ru/en, в том числе переводы декларативных вкладов плагинов.
- [x] macOS: unified toolbar, нативное меню.
- [x] Палитра команд (Ctrl+Shift+A) поверх `ActionManager`.
- [x] Диалог установки плагина с предупреждением о правах; включение/отключение плагинов в UI (перезапуск).

Архитектура настроек: `ConfigurableProvider` публикует метаданные без активации плагина; `Configurable` и Swing-компонент
создаются только при выборе страницы. Декларативные страницы объявляются в `contributions.settings`; scoped-менеджер
снимает их при выгрузке плагина. `Apply` затрагивает только открытые изменённые страницы. SPI с этим API — `0.9.0`.

Архитектура тем: `McsThemeManager` материализует встроенные `light.json`/`dark.json` в `.manuscriptium/themes/`, читает
пользовательские JSON-токены и применяет выбранную тему на EDT через FlatLaf без перезапуска. Режим `system` использует
кроссплатформенный детектор ОС без native-зависимостей; один daemon-поток следит за каталогом тем и системным режимом.
Выбор сохраняется в `AppSettings`, страница «Оформление» использует общий `ConfigurableProvider`; SPI остаётся `0.9.0`.

Архитектура иконок: `Icons.load(Class, path)` использует `FlatSVGIcon`, а `ColorFilter` заменяет монохромный
`currentColor` на foreground компонента при каждой отрисовке, поэтому смена темы не требует новых экземпляров иконок.
Поля `icon` в `plugin.yml` поддерживаются у действий, групп и tool windows; `PluginIcons` читает SVG прямо из JAR без
активации плагина. Пакет ресурсов named-модуля открывается через `opens`; JSVG объявлен явно для jlink. SPI не меняется.

Архитектура macOS: `MacOsSupport` задаёт screen menu bar, имя приложения и системный appearance до инициализации AWT.
Unified toolbar использует full window content, прозрачный заголовок и скрытый title; внешний контейнер сохраняет отступ
70 px под кнопки окна при любом `ActionToolbar.refresh()`. Нативные About/Preferences/Quit подключены через `Desktop`
к общим операциям ядра. Дубли не добавляются в Swing-меню, но действия остаются в `ActionManager` и палитре команд.
Quit отменяет автоматический выход ОС и проходит обычный `WINDOW_CLOSING` с проверкой несохранённых редакторов.
Windows/Linux сохраняют прежние меню и toolbar; SPI остаётся `0.9.0`.

Архитектура управления плагинами: `PluginState` хранит только отключённые ID в `state/core.plugins.yml`; плагины включены по умолчанию.
`PluginManager.discover()` показывает весь реестр, а `loadAll()` объявляет вклады только включённых плагинов. `PluginInstaller`
просматривает папку, ZIP или JAR без загрузки байткода, устанавливает через staging с rollback и защищает от path traversal,
symlink и небезопасных ID. Новый плагин появляется сразу; обновление существующего сохраняется в pending и атомарно применяется
`CoreBootstrap` при следующем запуске до открытия JAR менеджером плагинов, поэтому блокировки файлов Windows не мешают замене.
Settings → Plugins показывает метаданные, зависимости, permissions, ошибки и совместимость SDK;
установка требует явного подтверждения полного доступа плагина к JVM. Изменения применяются после перезапуска. `AppRestarter`
сохраняет JVM- и application-аргументы, отбрасывает служебные launch-параметры JLI и проверяет несохранённые редакторы до
запуска нового процесса. При отмене или ошибке текущий процесс остаётся открытым; SPI остаётся `0.9.0`.

- **Готово, когда:** темы переключаются без перезапуска; демо-плагин «Настройки» добавляет свою страницу; палитра находит действия.

### Фаза 3. Плагины (полный цикл)
- [x] Граф зависимостей (`depends-on`), проверка `sdk-version`, ошибки загрузки в UI.
- [x] Bundled-плагины в `system-plugins/`, внешние — `.manuscriptium/plugins/`.
- [x] Документация SDK и шаблон проекта плагина.

Архитектура проверки плагинов: `PluginManager.loadAll()` отбрасывает повреждённые манифесты и некорректные либо
несовместимые `sdk-version` до регистрации декларативных вкладов. `PluginOrdering` отклоняет duplicate ID, циклы и
зависимости от отсутствующих, отключённых, несовместимых или уже отклонённых плагинов; dependent тоже не объявляется.
Повреждённый `plugin.yml` представлен в реестре служебной строкой, поэтому Settings → Plugins не скрывает ошибку и
показывает статус Error либо Incompatible SDK с исходной причиной в деталях. Код и вклады отклонённых плагинов не
активируются. SPI остаётся `0.9.0`.

Архитектура источников плагинов: `PluginManager` принимает список каталогов и строит по ним единый реестр и граф,
поэтому зависимости работают между bundled и внешними плагинами, а duplicate ID между источниками отклоняется. Образ
содержит Hello в `image/system-plugins/`; runtime определяет этот каталог через `java.home`. Внешние плагины остаются в
`<home>/plugins/`; только там работают установка и pending-обновления. Для Gradle run и нестандартных launcher есть
`manuscriptium.systemPlugins`, а `manuscriptium.home` заменяет dev-аргумент `--home`, чтобы абсолютный путь сборочной
машины не попадал в launcher образа. SPI остаётся `0.9.0`.

SDK задокументирован в `docs/plugin-sdk.md`: сборка, JPMS, манифест, lifecycle, вклады, i18n, иконки, контекст,
состояние, зависимости и ограничения безопасности. `plugin-template/` — standalone Gradle-проект, подключающий
`ru.mcs:spi` через composite build с `compileOnly` и собирающий один JAR с `plugin.yml` в корне. Шаблон демонстрирует
`onDemand`, действие, tool window, SVG и ru/en. CI собирает шаблон и проверяет его как внешний плагин рядом с
bundled Hello. Сетевой репозиторий по-прежнему не входит в этот этап.

### Фаза 4. Vault и иерархия
- [x] Фундамент Vault: `.md` как источник истины; рекурсивный FileWatcher (`WatchService`); пересобираемый SQLite-кэш; открытие Vault и физическое дерево заметок.
- [x] Lucene: полнотекстовый поиск по контенту и метаданным, фильтры Front Matter (`draft`, теги) и поддерева, открытие результата.
- [x] Виртуальные деревья `.manuscriptium/views/*.yml` (ББК, авторы…): записи `node`/`file`, `override-title/visibility/tags`.
- [x] Клонирование: Ctrl+DnD добавляет ссылку на файл в другую ветку, обычный DnD — перенос ссылки; физический файл не перемещается.
- [x] Ленивая загрузка дерева (`TreeWillExpandListener` + `SwingWorker`), рендерер с иконкой-оверлеем клона.
- [x] Создание Markdown-заметок через File, toolbar и popup физического дерева, с автоматическим созданием вложенных каталогов.
- [x] Popup по левому/правому щелчку; открытие файла, clone/move ссылки через диалог допустимых целей.
- [x] Clone/move из физической вкладки Files, включая создание первого view и перенос ссылки между views без ручного YAML.
- [x] Сохранение выбранного языка и явный запрос перезапуска после фактической смены языка.
- [x] Демо-плагины «Заметки», «Свойства» (front matter как виджеты).
- [x] Совместимость с Obsidian в source-редакторе: `[[wikilinks]]`, `![[embed]]`, callouts, навигация и предпросмотр изображений.
- [x] Демо-плагины «Заметки», «Свойства» (front matter как виджеты).

Архитектура фундамента Vault: открывается весь выбранный каталог, поэтому существующие Obsidian Vault работают без импорта;
Markdown-файлы сканируются рекурсивно без следования symlink, а `.git` и `.manuscriptium` исключаются. Первичный ключ индекса —
относительный путь с `/` на всех ОС. `<vault>/.manuscriptium/index.db` (SQLite/Xerial, WAL) хранит заголовок, исходный Front Matter,
текст, размер и mtime; удаление БД безопасно, начальное сканирование полностью пересобирает её из `.md`. Невалидный YAML или не-UTF-8
файл не прерывает индексацию остальных заметок. Рекурсивный `WatchService` регистрирует новые каталоги, обрабатывает удаление дерева,
а `OVERFLOW` вызывает полную пересборку. Сканирование и обновления выполняются последовательно в daemon-потоке, не на EDT.

`File → Open Vault…` открывает панель с физическим деревом; двойной щелчок и Enter используют существующий `FileEditorManager`.
Последний Vault сохраняется глобально в `core.vault`, а сессия вкладок переведена в `Scope.VAULT` и хранится в
`<vault>/.manuscriptium/state/core.session.yml`. При переключении старая live-сессия сначала сохраняется, live-поставщик отключается,
и только затем загружается состояние нового Vault. Прежний `<home>/workspace/state` читается как legacy до первого открытия Vault.
SPI остаётся `0.9.0`.

Архитектура виртуальных views: каждый `.manuscriptium/views/<id>.yml` содержит `name` и упорядоченные `entries`.
Запись — строго `node` либо `file`; `children` есть только у узла. `override-title` влияет лишь на renderer,
`visibility: false` скрывает запись и всю ветку, `tags` остаются метаданными ссылки. Пути файлов относительные,
с `/` на всех ОС, без абсолютных путей, `.` и `..`; отсутствующий Markdown отображается как missing и не удаляется из YAML.
SnakeYAML SafeConstructor запрещает небезопасные типы и duplicate keys. Ошибка одного файла попадает на вкладку ошибок,
а остальные views и физические заметки продолжают работать. Сохранение атомарно через temp-файл в том же каталоге,
без YAML anchors у клонов. Watcher отдельно наблюдает `views`, остальная `.manuscriptium` по-прежнему не индексируется.

`VaultSnapshot` атомарно передаёт в UI заметки, структурные копии views и ошибки. Индексирование, reload и DnD сериализуются
одним executor. Перенос/клонирование разрешено только для файловой ссылки внутри того же view; drop в корень и узел поддержан,
дубликат непосредственно в целевом узле запрещён. Операция проверяет ожидаемый путь источника, чтобы устаревший Swing-path
после внешнего редактирования не перенёс другую ссылку. Клоном считается файл минимум с двумя видимыми ссылками во всех views;
renderer добавляет overlay, не изменяя Markdown. Обычный drop — `MOVE`, Ctrl/`COPY` — clone.

Физическая вкладка Files материализует Swing-узлы по снимку SQLite только при раскрытии через `TreeWillExpandListener`
и `SwingWorker`; программно раскрытый root материализуется явно. Виртуальные views получают отдельные вкладки с сохранением
раскрытия и выделения при обновлениях. Полнотекстовый поиск использует тот же снимок Markdown и последовательный executor Vault;
SPI остаётся `0.9.0`.

`MarkdownNoteCreator` использует общую с views проверку относительных путей, создаёт родительские каталоги по одному без
следования symlink и пишет UTF-8 заголовок через `CREATE_NEW`, поэтому существующий файл не перезаписывается. Создание
выполняется в `SwingWorker`, после чего заметка открывается через `FileEditorManager`. Действие доступно в File, toolbar
и popup физического дерева; `ActionManagerImpl.updateActions()` обновляет toolbar при смене Vault и состояния создания.

`TreePopupSupport` различает одиночный и двойной левый щелчок таймером и поддерживает платформенный popup trigger правой
кнопки. `ViewTargetChooser` проверяет каждую цель на структурной копии через ту же `ViewOperations`, что и DnD: в список
попадают корень и видимые узлы, но не текущий родитель, дубликаты или скрытые ветки. UI виртуального view использует
`VaultService.moveOrClone`, поэтому физический Markdown остаётся неизменным.

Из Files команды clone/move работают со снимком всех views: `PhysicalLinkSources` различает несколько ссылок на один файл,
а `PhysicalLinkTargetChooser` предлагает допустимые цели во всех views и создание нового view. `VaultService.addLink` и
`VaultService.moveLink` выполняются на том же последовательном executor, что reload и DnD. Перенос из Files поддерживает цель
в том же или другом view; новый YAML создаётся без `REPLACE_EXISTING`, с уникальным ASCII-id и отдельной атомарной записью
каждого файла. После успеха открывается целевой view. Физический Markdown не изменяется.

`GeneralSettingsProvider` вызывает restart callback только если сохранённое значение языка действительно изменилось. Callback
подключается в `CoreBootstrap` к общему `PluginRestartController`, поэтому Settings сохраняет состояние и затем предлагает
перезапуск; `CoreServices` по-прежнему инициализирует `Messages` из сохранённого языка до создания UI. SPI остаётся `0.9.0`.

`ObsidianSyntax` — неблокирующий лексер только нужных конструкций: он возвращает UTF-16 offsets wikilinks, embeds и
callouts, игнорируя BOM/front matter, inline code и fenced code. `ObsidianEditorSupport` лишь добавляет Highlighter к
редактируемому `JTextArea`, поэтому исходный Markdown остаётся источником истины. Ctrl/Cmd+Click открывает заметку,
`#заголовок` или `^block-id`; `ObsidianLinkResolver` ищет sibling/root-relative/shortest-path совпадения без учёта регистра,
но отклоняет выход за Vault и symlink. `ObsidianLinkNavigator` разрешает пути и декодирует PNG/JPEG/GIF/BMP/SVG вне EDT,
обновляет список заметок из атомарного `VaultSnapshot` и игнорирует завершение операции после смены Vault. Embed заметки
открывает её, изображение показывает в немодальном preview, остальные attachments передаёт ОС. Callout-подсветка не
сворачивает блоки; полноценный rendered/WYSIWYG-preview остаётся в Фазе 5. SPI и набор зависимостей не изменились.

Архитектура поиска: `LuceneVaultIndex` — второй пересобираемый кэш внутри `VaultIndex`, расположенный в
`.manuscriptium/lucene` и использующий только `lucene-core` 10.6.0 (`NIOFSDirectory`, без native-access и queryparser).
Начальный `rebuild()` за один список scanned Markdown обновляет SQLite и Lucene; `refresh()` инкрементально обновляет или
удаляет документ в обоих кэшах. Идентификатор — тот же относительный путь с `/`. Отдельные TextField для тела, Front Matter,
заголовка, пути и тегов исключают неоднозначность области Content; `draft` и путь-id служат structured-фильтрами.
Невалидный YAML не блокирует индексацию текста. Пользовательский ввод анализируется `StandardAnalyzer` как литеральные
слова без query syntax; слова соединяются по AND, title/tags получают больший вес, лимит — 100 результатов.

`VaultService.search()` ставит поиск в тот же однопоточный executor, что rebuild/refresh, поэтому Lucene не становится
независимым источником истины и поиск не выполняется на EDT. Bottom tool window `core.search` поддерживает области,
`draft`/поддерево и открытие через `FileEditorManager`; generation и identity backend отбрасывают результаты прежнего
запроса и прежнего Vault. Удаление `lucene/` при остановленном приложении безопасно: следующее открытие полностью
пересоздаёт кэш из Markdown. JPMS требует только `org.apache.lucene.core`; SPI остаётся `0.9.0`, зависимости плагинов не менялись.

Bundled `plugins:notes` и `plugins:properties` — независимые `onDemand` JPMS-модули, зависящие только от `spi`; общий
список bundled-плагинов в `app/build.gradle` копирует их JAR и `plugin.yml` в `system-plugins` и jlink-образ. «Заметки»
подписывается на `EditorTopics.EDITOR`, открывает существующие вкладки через `FileEditorManager`, копирует короткую
`[[wikilink]]` и считает статистику в `SwingWorker`, отбрасывая устаревшие результаты. «Свойства» не регистрирует свой
FileEditorProvider и поэтому сохраняет Obsidian-подсветку и навигацию source-редактора. Его line-based FrontMatter
изменяет только `title`, `tags`, `draft`, сохраняя body, BOM, CRLF и неизвестные YAML-строки; duplicate managed keys,
незакрытый front matter и неподдерживаемые значения отклоняются. Apply выполняет `saveAllAsync()`, ожидает фоновое сохранение редакторов, затем повторно читает
и записывает в фоне через temp + `ATOMIC_MOVE`, отклоняет symlink и переоткрывает файл только если он остался активным.
Новый front matter создаётся лишь при непустых управляемых значениях. Тесты покрывают parser, symlink, Swing-панели и
полный Apply; smoke CI проверяет все три bundled-плагина. SPI остаётся `0.9.0`, зависимости плагинов не расширялись.

`ObsidianSyntax` — неблокирующий лексер только нужных конструкций: он возвращает UTF-16 offsets wikilinks, embeds и
callouts, игнорируя BOM/front matter, inline code и fenced code. `ObsidianEditorSupport` лишь добавляет Highlighter к
редактируемому `JTextArea`, поэтому исходный Markdown остаётся источником истины. Ctrl/Cmd+Click открывает заметку,
`#заголовок` или `^block-id`; `ObsidianLinkResolver` ищет sibling/root-relative/shortest-path совпадения без учёта регистра,
но отклоняет выход за Vault и symlink. `ObsidianLinkNavigator` разрешает пути и декодирует PNG/JPEG/GIF/BMP/SVG вне EDT,
обновляет список заметок из атомарного `VaultSnapshot` и игнорирует завершение операции после смены Vault. Embed заметки
открывает её, изображение показывает в немодальном preview, остальные attachments передаёт ОС. Callout-подсветка не
сворачивает блоки; полноценный rendered/WYSIWYG-preview остаётся в Фазе 5. SPI и набор зависимостей не изменились.

Bundled `plugins:notes` и `plugins:properties` — независимые `onDemand` JPMS-модули, зависящие только от `spi`; общий
список bundled-плагинов в `app/build.gradle` копирует их JAR и `plugin.yml` в `system-plugins` и jlink-образ. «Заметки»
подписывается на `EditorTopics.EDITOR`, открывает существующие вкладки через `FileEditorManager`, копирует короткую
`[[wikilink]]` и считает статистику в `SwingWorker`, отбрасывая устаревшие результаты. «Свойства» не регистрирует свой
FileEditorProvider и поэтому сохраняет Obsidian-подсветку и навигацию source-редактора. Его line-based FrontMatter
изменяет только `title`, `tags`, `draft`, сохраняя body, BOM, CRLF и неизвестные YAML-строки; duplicate managed keys,
незакрытый front matter и неподдерживаемые значения отклоняются. Apply выполняет `saveAllAsync()`, ожидает фоновое сохранение редакторов, затем повторно читает
и записывает в фоне через temp + `ATOMIC_MOVE`, отклоняет symlink и переоткрывает файл только если он остался активным.
Новый front matter создаётся лишь при непустых управляемых значениях. Тесты покрывают parser, symlink, Swing-панели и
полный Apply; smoke CI проверяет все три bundled-плагина. SPI остаётся `0.9.0`, зависимости плагинов не расширялись.

### Canvas-редактор E0–E9

Основной маршрут — `EDITOR_CANVAS_PLAN.md`; функциональные идентификаторы и приоритеты — `TYPORA_FEATURES.md`.
- [x] **E0. Каркас:** `plugins/markdown-editor`, `MarkdownFileEditorProvider`, `MarkdownFileEditor`, собственный `EditorCanvas`,
  режимы Canvas/Source внутри одной вкладки, действие переключения, настройка режима по умолчанию, ru/en и headless-тесты.
- [x] **E1. Текстовое ядро:** Piece Table `TextBuffer`, `DocumentModel` и команды insert/delete/replace/composite,
  вложенные транзакции, undo/redo с паузой 500 мс и группировкой по типу, save-point и modified, UTF-16 Position/Range,
  строгий UTF-8, BOM и EOL. `AtomicFileStore` в SPI/core выполняет атомарную запись в target symlink; UI-сценарии
  используют фоновое `saveAsync()`. Property-тесты сверяют правки со `StringBuilder`, полный undo/redo и правки файла 5 МиБ.
- [x] **E2. Ввод и простой рендер:** оба режима редактируются собственным lightweight canvas без `JTextArea`; каретка,
  selection мышью/клавиатурой, drag/double/triple click, word wrap, Home/End/PageUp/PageDown, clipboard, Ctrl/Cmd+Z/Y,
  IME и инерционный скролл. `DocumentLineIndex` и `TextLayoutEngine` перестраивают только затронутые строки, batch composite
  перестраивается один раз, рисование ограничено viewport; есть метрики кадра и тесты `hitTest`/`caretRect`, включая scale=2.
- [x] **E3. Парсер и дерево блоков:** отдельный bundled `plugins/markdown-parser` на Flexmark 0.64.8; JDK-only SPI
  `MarkdownParser`, `BlockSpan`, `BlockKind`, `AstIndex`; Front Matter, wikilinks/embeds, callouts, footnotes, math, task lists,
  tables и strikethrough с точными UTF-16 spans. Incremental dirty-window переиспользует неизменённые блоки, а фоновый
  `MarkdownParseCoordinator` публикует на EDT только актуальную версию. Corpus CommonMark/Obsidian и 1000 randomized-правок
  сравниваются с полным AST; одиночная обычная правка перепарсивает один блок.
- [x] **E4. Движок стилей:** отдельный bundled `plugins/editor-styles` (`ru.mcs.plugin.editorstyle`) предоставляет
  `EditorStyleService` через JDK-only SPI. Поддержаны Typora-подобный CSS, каскад `DEFAULT → THEME → BASE_USER → THEME_USER`,
  переменные, селекторы, типизированный `ComputedStyle`, локальные `@font-face` и нефатальные `CssDiagnostic`. Девять тем
  (`github`, `newsprint`, `night`, `pixyll`, `whitey`, `vue`, `lapis`, `onedark`, `notion`) загружаются без ошибок; в auto-режиме
  Light/Dark оболочки отображаются в `github`/`night`. Пользовательские `base.user.css` и `{theme}.user.css` читаются из
  `<home>/editor-themes`; CSS и шрифты загружаются вне EDT. Canvas пока отображает исходный Markdown — применение стилей при
  раскладке относится к E5.
- [ ] **E5–E9:** выполняются последовательно по отдельному плану. Публикация и Фаза 6 начинаются только после завершения E9.

Принятые решения: плагин не зависит от `core`; Obsidian-навигация и preview будут предоставлены через SPI-сервисы и адаптер
`core`. `flexmark-java`, CSS-движок и `JLaTeXMath` становятся отдельными JAR-плагинами с JDK-only сервисными контрактами SPI
и связями `depends-on`. F-007, F-008, полный F-060, F-062 и outline из F-100 перенесены в E9. Рендеринг проверяется метриками
с встроенным шрифтом, записью `DrawSink` и платформенными golden-изображениями с допусками/diff-артефактами; пиксельное
совпадение между ОС не требуется. В E0–E1 Source только читался; с E2 Canvas и Source редактируются и используют общую
модель, отображая исходный Markdown до появления WYSIWYG-раскладки. Внутренний текст нормализован к LF, смешанные EOL не
сохраняются без явной нормализации. Добавление `AtomicFileStore` и default-методов асинхронного сохранения бинарно
совместимо с плагинами линии 0.9. В E3 parser-контракты добавлены в SPI аддитивно; `McsSdk.VERSION` и `sdk-version` остаются
`0.9.0`/`0.9`. Flexmark и его runtime-JAR находятся только в отдельном plugin layer, `markdown-editor` получает parser через
`ServiceRegistry` и `depends-on`, не приобретая compile-time зависимость на стороннюю библиотеку. В E4 CSS-движок выделен в
самостоятельный plugin layer, использующий только JDK и SPI; старые JSON/FlatLaf-темы оболочки сохранены. `ShellThemeService` —
read-only bridge из `core` с listeners, а явный выбор editor theme отключает автоматическое Light/Dark-отображение. Каскад сортирует
декларации по `!important`, specificity, origin, порядку правила и декларации. Локальные шрифты разрешаются только внутри каталога
темы; внешние URL и неподдерживаемые CSS-конструкции дают диагностику, но не останавливают загрузку. Editor style state хранится
в application state и не попадает в синхронизируемый Vault.

### Фаза 5. Публикация (после Canvas E0–E9)
- Публикация: `StaticSiteExporter` (Hugo), `PublishingProfile`, валидация YAML, конвертация `![[img]]`, проверка `draft`, битые ссылки, JGit commit/push в фоне со статусом в статус-баре.
- Мультиязычность `index.md`/`index.en.md`.

### Фаза 6. Расширения
- Скрипты (`McsFacade`), AI-ассистент (перевод и пр.), JCEF-редактор как плагин, граф, Kanban, синхронизация (`SyncProvider`, E2EE AES-GCM), рантайм-компиляция Java (учесть `jdk.compiler` и classpath в jlink-образе).

## 5. Риски и контрмеры

| Риск | Контрмера |
|---|---|
| Немодульные зависимости ломают jlink | Merged-модуль плагина beryx; прототип в Фазе 0 |
| `ServiceLoader` не видит классы слоёв плагинов | Только `ServiceLoader.load(layer, Class)` |
| Иконки плагина не загружаются в JPMS | Пакет иконок открывать в `module-info`; `Icons.load` по URL |
| Records в публичном API ломают плагины | Semver, `sdk-version`, `@since`, избегать изменения полей |
| Утечки ClassLoader при выгрузке | Тяжёлые плагины — только с перезапуском |
| Нет настоящей песочницы | Capability API + честное предупреждение пользователю |
| macOS unified toolbar | Отступ под кнопки окна, проверка на реальном Mac |

## 6. Открытые вопросы (не блокируют Фазу 0)
- Keymap (настраиваемые горячие клавиши) — после Фазы 2.
- Выбор редактора MVP (RSyntaxTextArea vs JTextPane) — решить прототипом в Фазе 5.
- Сплиты редактора, плавающие панели — после MVP.
