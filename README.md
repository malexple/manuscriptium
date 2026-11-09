# Manuscriptium

Фаза 0: каркас Gradle/JPMS, загрузка плагинов через ModuleLayer, jlink-образ, CI на 3 ОС.

## Первый запуск
```
gradle wrapper --gradle-version 8.14.4   # нужен Gradle >= 8.14.4 для плагина jlink 4.x
./gradlew build
./gradlew :app:run                       # окно с панелью из плагина hello
./gradlew :app:devHome :app:jlink
app/build/image/bin/manuscriptium --smoke --home=app/build/dev-home
```
