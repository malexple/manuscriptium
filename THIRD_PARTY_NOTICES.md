# Сторонние ресурсы и лицензии

Исходный код и собственные ресурсы Manuscriptium распространяются по [MIT License](LICENSE).
Ниже перечислены сторонние ресурсы, хранящиеся непосредственно в репозитории. Этот файл не заменяет
лицензии зависимостей, которые Gradle загружает из Maven Central.

## Lucide Icons

SVG-иконки Lucide распространяются по ISC License. Лицензии находятся рядом с ресурсами и включаются
в соответствующие JAR:

- [иконки оболочки](core/src/main/resources/ru/mcs/core/icons/lucide/LICENSE.txt);
- [иконки Markdown-редактора](markdown/src/main/resources/ru/mcs/markdown/editor/icons/LICENSE.txt);
- [иконки Notes](markdown/src/main/resources/ru/mcs/markdown/notes/icons/LICENSE.txt);
- [иконки Properties](markdown/src/main/resources/ru/mcs/markdown/properties/icons/LICENSE.txt);
- [иконка standalone plugin-template](plugin-template/src/main/resources/ru/mcs/plugin/template/icons/LICENSE.txt).

Полные notices оболочки и Markdown-редактора также содержат MIT License для производных иконок Feather.

## Liberation Sans

`LiberationSans-Regular.ttf` используется только в тестах рендеринга и не входит в production runtime image.
Шрифт распространяется по GNU GPL v2 с font exception; сохранён
[полный исходный notice](markdown/src/test/resources/fonts/LiberationSans-LICENSE.txt). Сгенерированные
тестовые изображения являются документами, отрисованными шрифтом, и не изменяют лицензию кода проекта.

## Встроенные темы

CSS-файлы `base`, `github`, `newsprint`, `night`, `pixyll`, `whitey`, `vue`, `lapis`, `onedark` и `notion`
в `markdown/src/main/resources/ru/mcs/markdown/style/themes/` созданы для Manuscriptium и распространяются
под MIT License проекта. Это компактные собственные правила, а не файлы из дистрибутива Typora; названия
обозначают визуальный стиль. Пользовательские `base.user.css` и `{theme}.user.css` загружаются из каталога
пользователя отдельно и не являются встроенными ресурсами Manuscriptium.
