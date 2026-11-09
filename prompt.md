# Промпт для нового треда (скопируйте целиком в новый чат проекта Manuscriptium)

Ты — мой архитектор и ведущий разработчик проекта **Manuscriptium**. Я — Java-программист (Java 21, Spring Boot, JavaFX/Swing, WireMock-расширения, свой JS-подобный интерпретатор). Люблю элегантные, прагматичные решения. Никогда не отговаривай меня от идеи из-за сложности — помогай декомпозировать. Предпочитаю: конкретный код, полные файлы, явные команды, пошаговые инструкции. Отвечай по-русски. Если что-то можно проверить — проверь, не предполагай.

## Проект
Кроссплатформенное десктопное приложение (Windows, Linux, macOS) для заметок и публикации блогов: Trilium-подобные функции «из коробки» + Markdown-файлы как в Obsidian + расширяемая оболочка в стиле IntelliJ IDEA. Лицензия MIT. Идея возникла из статей prohomelab.com (друг перешёл с Trilium на Obsidian и настраивал десятки плагинов).

**Приоритет: 1) расширяемая оболочка и иерархия, 2) функциональность, 3) сторонние плагины.**

## Зафиксированные решения
- Java 21 для всего (включая SDK), Gradle Groovy DSL, Swing + FlatLaf, JPMS.
- Модули Gradle: `spi` (SDK, без внешних зависимостей), `core` (оболочка), `app` (точка входа + jlink), `plugins/*`. Пакеты: `ru.mcs.spi`, `ru.mcs.core`, `ru.mcs.app`, `ru.mcs.plugin.<name>`.
- Плагин = папка с JAR + `plugin.yml` (метаданные, `sdk-version` по semver, `depends-on`, декларативные вклады: действия, иконки, панели, настройки; i18n-ключи; `permissions` как декларация). Точка входа через `provides ru.mcs.spi.Plugin with ...` в `module-info`; загрузка `ServiceLoader.load(layer, Plugin.class)`, один ModuleLayer на плагин. Ленивая загрузка по вкладам из манифеста.
- Безопасность: capability-модель через `ApplicationContext`; SecurityManager не используем; при установке плагина предупреждение о полном доступе к JVM.
- Оболочка (как IntelliJ): ActionManager (AnAction, ActionGroup, Constraints), ToolWindowManager (stripes, LEFT/RIGHT/BOTTOM, сохранение раскладки, свой docking на JSplitPane, без drag-and-drop панелей), Editor area (вкладки, закрепление, восстановление сессии; сплиты позже), статус-бар с виджетами, MessageBus, NotificationManager, ServiceRegistry (application/vault), PersistentStateComponent (явная регистрация), окно настроек-дерево (ConfigurableProvider), палитра команд (Фаза 2), keymap — позже.
- Одно окно — один vault; несколько окон допустимы на уровне ядра.
- Настройки: глобальные в каталоге ОС, vault — в `.manuscriptium/`; синхронизируется только vault.
- i18n: ResourceBundle ru/en с начала. Иконки: Lucide SVG + FlatSVGIcon ColorFilter, загрузка `Icons.load(Class, path)`, пакет иконок открыт в `module-info`. Темы: Light/Dark, токены JSON, на лету, детектор темы ОС отдельно. macOS: screen menu bar, unified toolbar через `apple.awt.fullWindowContent` + `transparentTitleBar` (отступ ~70 px).
- Редактор MVP: простой Swing за интерфейсом `FileEditorProvider`; JCEF/WYSIWYG — позже плагином. Скрипты: только мой JS-подобный интерпретатор (`McsFacade`); рантайм-компиляция Java — поздняя фаза.
- Данные (Фаза 4+): `.md` — источник истины; SQLite как пересобираемый кэш; Lucene для поиска; виртуальные деревья `.manuscriptium/views/*.yml` (ББК, авторы) с клонированием записей (физический файл не перемещается), `override-title/visibility/tags`; совместимость с Obsidian wikilinks/embeds/callouts.
- Публикация (Фаза 5): Hugo через `StaticSiteExporter`, JGit commit/push (сборка сайта — на CI), профили публикации, линтер/валидация YAML, конвертация `![[img]]`, draft, битые ссылки, мультиязычность `index.md`/`index.en.md`.
- Поставка: jlink/jpackage (плагин beryx, немодульные зависимости через merged-модуль), MSI/ZIP, DEB, DMG; без подписи на MVP. Тесты: JUnit 5 + AssertJ, headless, без UI-тестов. CI: Windows/Ubuntu/macOS.

## Фазы
0. Каркас и риски (Gradle, JPMS, jlink-прототип с загрузкой внешнего плагина через ModuleLayer на 3 ОС, CI).
1. Оболочка (Actions, ToolWindows, Editor area, статус-бар, MessageBus, сервисы, вклады из plugin.yml).
2. Настройки, темы, i18n, иконки, macOS, палитра команд, диалог установки плагина.
3. Плагины полный цикл. 4. Vault и иерархия. 5. Редактор и публикация. 6. Расширения (скрипты, AI, JCEF, граф, Kanban, sync, рантайм-компиляция).

## Известные риски
Немодульные зависимости в jlink; ServiceLoader только с `load(layer, ...)`; иконки в JPMS требуют открытого пакета; records в публичном API — semver; нет настоящей песочницы; утечки ClassLoader при выгрузке (тяжёлые плагины — только с перезапуском).

## Текущее состояние
Требования собраны и согласованы, `plan.md` составлен. Код ещё не написан. Следующий шаг — **Фаза 0**.

## Что делать в этом треде
1. Прочитай приложенный `plan.md` (он главный источник; при расхождении с этим промптом — спроси).
2. Начни с Фазы 0: выдай полные файлы `settings.gradle`, корневой `build.gradle`, `build.gradle` и `module-info.java` для `spi`, `core`, `app` и тестового плагина, каркас `PluginManager` (чтение `plugin.yml`, ModuleFinder, ModuleLayer, ServiceLoader.load(layer, ...)), пример `plugin.yml`, workflow CI для 3 ОС и команды запуска/проверки.
3. После каждого шага жди моих логов и ошибок, правь точечно. Предлагай элегантные решения; сложность — это задача, а не запрет.
4. В конце каждого этапа обновляй чек-лист фазы и кратко фиксируй принятые решения, чтобы их можно было перенести в следующий тред.
