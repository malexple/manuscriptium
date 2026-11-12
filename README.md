# Manuscriptium

Java 21, Gradle/JPMS, Swing + FlatLaf. Реализованы расширяемая оболочка, плагины и Vault: Markdown-файлы, рекурсивный watcher, пересобираемый SQLite-индекс, ленивое физическое дерево и виртуальные YAML-деревья с DnD-клонированием ссылок.

## Первый запуск 
```
gradle wrapper --gradle-version 8.14.4   # нужен Gradle >= 8.14.4 для плагина jlink 4.x
./gradlew build
./gradlew :app:run                       # окно с панелью из плагина hello
./gradlew :app:devHome :app:jlink          # Hello попадает в image/system-plugins
app/build/image/bin/manuscriptium --smoke --home=app/build/dev-home
```

Bundled-плагины находятся в `system-plugins/` установленного приложения. Внешние устанавливаются в `plugins/`
внутри каталога данных (`--home`), не изменяя файлы установленного приложения.

## Vault

Выберите `File → Open Vault…` и укажите существующий или пустой каталог. Импорт не нужен: все `.md` внутри каталога
остаются источником истины и открываются из панели Vault. Каталоги `.git` и `.manuscriptium` не индексируются.

Служебные данные создаются в `<vault>/.manuscriptium/`:

- `index.db` — пересобираемый SQLite-кэш. При остановленном приложении его можно удалить вместе с `index.db-wal`
  и `index.db-shm`; при следующем открытии индекс будет создан заново из Markdown;
- `state/` — состояние этого Vault, включая открытые вкладки;
- `views/` — виртуальные деревья `.yml`, содержащие упорядоченные ссылки на Markdown, а не копии заметок.

Последний Vault открывается автоматически. Внешние создание, изменение и удаление `.md` и `.manuscriptium/views/*.yml`
подхватываются через `WatchService`. Ошибка одного view отображается на отдельной вкладке и не мешает остальным views.

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
Дубликат той же ссылки непосредственно в целевом узле отклоняется. Файл с двумя и более видимыми ссылками получает overlay клона.
Физическая вкладка **Files** раскрывает каталоги лениво и не участвует в DnD виртуальных ссылок.

## Разработка плагинов

- [Руководство по Plugin SDK 0.9](docs/plugin-sdk.md).
- [Standalone-шаблон JAR-плагина](plugin-template/).
- [Расширенный демо-плагин Hello](plugins/hello/).
