# Manuscriptium — план реализации (plan.md)

Версия документа: 1.1 (2026-11-12). Статус: Фазы 0–3 завершены, идёт Фаза 4.

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
| Редактор (MVP) | Интерфейс `FileEditorProvider`/`FileEditor`; простой Swing-редактор (кандидат RSyntaxTextArea; проверить Markdown-режим на прототипе). JCEF + WYSIWYG — внешний плагин в поздней фазе |
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
- [ ] Lucene — позже в этой фазе.
- [x] Виртуальные деревья `.manuscriptium/views/*.yml` (ББК, авторы…): записи `node`/`file`, `override-title/visibility/tags`.
- [x] Клонирование: Ctrl+DnD добавляет ссылку на файл в другую ветку, обычный DnD — перенос ссылки; физический файл не перемещается.
- [x] Ленивая загрузка дерева (`TreeWillExpandListener` + `SwingWorker`), рендерер с иконкой-оверлеем клона.
- [x] Создание Markdown-заметок через File, toolbar и popup физического дерева, с автоматическим созданием вложенных каталогов.
- [x] Popup по левому/правому щелчку; открытие файла, clone/move ссылки через диалог допустимых целей.
- [x] Clone/move из физической вкладки Files, включая создание первого view и перенос ссылки между views без ручного YAML.
- [x] Сохранение выбранного языка и явный запрос перезапуска после фактической смены языка.
- [x] Совместимость с Obsidian в source-редакторе: `[[wikilinks]]`, `![[embed]]`, callouts, навигация и предпросмотр изображений.
- [x] Демо-плагины «Заметки», «Свойства» (front matter как виджеты).
- [x] Совместимость с Obsidian в source-редакторе: `[[wikilinks]]`, `![[embed]]`, callouts, навигация и предпросмотр изображений.
- Демо-плагины «Заметки», «Свойства» (front matter как виджеты).

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
раскрытия и выделения при обновлениях. Lucene в этот инкремент не входит; SPI остаётся `0.9.0`.

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

Bundled `plugins:notes` и `plugins:properties` — независимые `onDemand` JPMS-модули, зависящие только от `spi`; общий
список bundled-плагинов в `app/build.gradle` копирует их JAR и `plugin.yml` в `system-plugins` и jlink-образ. «Заметки»
подписывается на `EditorTopics.EDITOR`, открывает существующие вкладки через `FileEditorManager`, копирует короткую
`[[wikilink]]` и считает статистику в `SwingWorker`, отбрасывая устаревшие результаты. «Свойства» не регистрирует свой
FileEditorProvider и поэтому сохраняет Obsidian-подсветку и навигацию source-редактора. Его line-based FrontMatter
изменяет только `title`, `tags`, `draft`, сохраняя body, BOM, CRLF и неизвестные YAML-строки; duplicate managed keys,
незакрытый front matter и неподдерживаемые значения отклоняются. Apply выполняет `saveAll()` на EDT, повторное чтение
и запись в фоне через temp + `ATOMIC_MOVE`, отклоняет symlink и переоткрывает файл только если он остался активным.
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

### Фаза 5. Редактор и публикация
- Markdown-редактор (Swing), линтер front matter, word count.
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
