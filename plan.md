# Manuscriptium — план реализации (plan.md)

Версия документа: 1.0 (2026-11-09). Статус: требования согласованы, старт — Фаза 0.

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
- [ ] `McsThemeManager`: Light/Dark, на лету, токены JSON, детектор темы ОС, перезагрузка файла темы при правке.
- [ ] `Icons.load` + `ColorFilter`, набор Lucide.
- [x] `ResourceBundle` ru/en, в том числе переводы декларативных вкладов плагинов.
- [ ] macOS: unified toolbar, нативное меню.
- [x] Палитра команд (Ctrl+Shift+A) поверх `ActionManager`.
- [ ] Диалог установки плагина с предупреждением о правах; включение/отключение плагинов в UI (перезапуск).

Архитектура настроек: `ConfigurableProvider` публикует метаданные без активации плагина; `Configurable` и Swing-компонент
создаются только при выборе страницы. Декларативные страницы объявляются в `contributions.settings`; scoped-менеджер
снимает их при выгрузке плагина. `Apply` затрагивает только открытые изменённые страницы. SPI с этим API — `0.9.0`.
- **Готово, когда:** темы переключаются без перезапуска; демо-плагин «Настройки» добавляет свою страницу; палитра находит действия.

### Фаза 3. Плагины (полный цикл)
- Граф зависимостей (`depends-on`), проверка `sdk-version`, ошибки загрузки в UI.
- Bundled-плагины в `system-plugins/`, внешние — `.manuscriptium/plugins/`.
- Документация SDK и шаблон проекта плагина.

### Фаза 4. Vault и иерархия
- Vault: `.md` как источник истины; FileWatcher (WatchService); SQLite как кэш (пересобираемый); Lucene — позже в этой фазе.
- Виртуальные деревья `.manuscriptium/views/*.yml` (ББК, авторы…): записи `node`/`file`, `override-title/visibility/tags`.
- Клонирование: Ctrl+DnD добавляет ссылку на файл в другую ветку, обычный DnD — перенос ссылки; физический файл не перемещается.
- Ленивая загрузка дерева (`TreeWillExpandListener` + `SwingWorker`), рендерер с иконкой-оверлеем клона.
- Совместимость с Obsidian: `[[wikilinks]]`, `![[embed]]`, callouts.
- Демо-плагины «Заметки», «Свойства» (front matter как виджеты).

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
- Формат пакета плагина (папка vs ZIP) — уточнить в Фазе 3.
- Сплиты редактора, плавающие панели — после MVP.
