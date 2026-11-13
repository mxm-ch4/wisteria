# Module API

Как написать модуль Wisteria. Этого файла и раздела спеки самого модуля достаточно: остальной репозиторий читать не нужно.
Клиент только под Minecraft 26.2, Fabric, Java 25. Игра не обфусцирована, имена классов — официальные Mojang.

## Что трогать

| Файл | Что |
|---|---|
| `src/main/java/dev/wisteria/modules/<имя>/` | весь код модуля — своя папка |
| `src/main/java/dev/wisteria/modules/Modules.java` | одна строка `new MyModule(),` |
| `src/main/java/dev/wisteria/mixin/<имя>/`, `src/main/resources/wisteria.mixins.json` | только если нужен миксин: класс и одна строка `"<имя>.MyMixin"` |
| `src/test/java/dev/wisteria/modules/<имя>/` | модульный тест |
| `src/gametest/java/dev/wisteria/gate/modules/`, `src/gametest/resources/fabric.mod.json` | автотест в игре и одна строка в `fabric-client-gametest` |

Всё остальное — **ядро**, его не меняем. Не хватает события или хелпера — это отдельная задача на ядро, а не правка по месту.

Начать проще всего с копии `modules/_template/TemplateModule.java` (в игре его нет, но он компилируется и покрыт тестом).
Рабочий пример — `modules/fullbright/FullBright.java`.

## Модуль

```java
public final class Nuker extends Module {
	private final DoubleSetting range = this.add(new DoubleSetting("Range", "Reach in blocks", 4.0, 1.0, 6.0, 0.5));

	public Nuker() {
		super("Nuker", "Breaks blocks around you", Category.WORLD);   // + Key.X последним аргументом — бинд по умолчанию
		this.on(Events.Tick.class, event -> this.tick());          // работает, только пока модуль включён
	}

	@Override protected void onEnable() { }    // подготовить состояние
	@Override protected void onDisable() { }   // вернуть всё, что модуль поменял в игре
}
```

Имя — это и подпись в ClickGUI, и команда в чате (`.nuker` переключает, `.nuker range 5` меняет настройку).
Описание — подсказка в GUI. Категории: `RENDER`, `WORLD`, `PLAYER`, `MOVEMENT`, `COMBAT`, `MISC` (и `HUD` — только для `HudElement`).
ClickGUI, бинды, сохранение включённости и настроек, команды — всё появляется само.

## Настройки

Объявляются полями через `this.add(...)`, читаются `.get()`. Значение всегда в границах: лишнее обрезается.

| Класс | Аргументы после имени и описания | В GUI |
|---|---|---|
| `BoolSetting` | default | тумблер |
| `IntSetting` | default, min, max | слайдер |
| `FloatSetting`, `DoubleSetting` | default, min, max, step | слайдер |
| `EnumSetting<E>` | default (константа своего enum) | выпадающий список |
| `ColorSetting` | default ARGB (`0xFFB79CFF`) | выбор цвета |
| `KeySetting` | — или default `Key` | запись клавиши |
| `StringSetting` | default, maxLength | текстовое поле |
| `ActionSetting` | надпись кнопки, действие | кнопка (не сохраняется) |
| `InputSetting` | подсказка, надпись, действие(текст) | поле и кнопка; ошибку действия (`IllegalArgumentException`) видно под полем |
| `ListSetting` | подсказка, список, добавить, удалить | поле Add и строки с кнопкой удаления; данные хранит тот, кто их дал |
| `PasswordSetting` | «задано ли», сохранить | поле с точками; из чата не принимается, не сохраняется в профиле |
| `ChoiceSetting` | default, варианты, сохранять ли | выпадающий список с вариантами на лету |

Реакция на изменение — `setting.onChange(value -> ...)`.
**Дефолты — в пределах Grim.** Агрессивное — отдельный `BoolSetting`, по умолчанию `false`, описание начинается с «Experimental:».

## События

Все — на **клиентском потоке**, кроме пакетов. «Отмена» — `event.cancel()`: событие не дойдёт до игры.

| Событие | Когда | Данные | Отмена |
|---|---|---|---|
| `TickStart`, `Tick` | каждый тик (20/с), до и после тика игры | — | — |
| `Render2D`, `Render3D` | каждый кадр: HUD и мир | `renderer()`, `tickDelta()` | — |
| `KeyInput` | клавиша | `key()`, `action()`, `inScreen()` | — |
| `PacketSend` | пакет уходит; **сетевой или клиентский поток** | `packet()`, `name()` | да |
| `PacketReceive` | пакет пришёл; **сетевой поток** | `packet()`, `name()` | да |
| `Join`, `Leave` | вход в мир и выход (любой): сбросить состояние | — | — |
| `ChunkLoad`, `ChunkUnload` | чанк пришёл с сервера / сейчас уйдёт | `chunk()` — `LevelChunk`, блоки читаются | — |
| `BlockUpdate` | блок в загруженном мире сменился (сервер или своё предсказание; не для целых чанков) | `pos()`, `oldState()`, `newState()` | — |
| `EntityAdd`, `EntityRemove` | сущность появилась в мире клиента / ушла (дальность, смерть) | `entity()` | — |
| `ContainerOpen` | сервер открыл контейнер | `containerId()`, `menu()` (`"minecraft:generic_9x3"`), `title()`, `pos()` | — |
| `ContainerContent` | пришло полное содержимое | `items()` (контейнер, затем инвентарь), `containerSlots()`, `containerItems()`, `pos()` | — |
| `ChatReceive` | строка в чат | `text()`, `message()`, `system()`, `sender()`, `overlay()` | да — строка не покажется |
| `Death`, `Respawn` | игрок умер (один раз) / снова жив | `position()`, `dimension()` | — |
| `ScreenOpen` | экран открывается (явно, не автоматический после закрытия) | `screen()` | да — не откроется |
| `ScreenClose` | экран закрывается или заменяется | `screen()` | — |
| `Disconnect` | сервер оборвал соединение (кик, бан, таймаут); до `Leave`. Свой выход — только `Leave` | `reason()`, `text()` | — |

`pos()` контейнера — блок, по которому игрок кликнул прямо перед открытием; `null` для сущностей и открытий сервером.
`packet()` — настоящий объект игры: `if (event.packet() instanceof ServerboundMovePlayerPacket move) ...`.
С сетевого потока игру не трогать: `this.platform().execute(() -> ...)`.

## Доступ к игре

- `this.player()`, `this.level()` — игрок и мир или `null`, если не в мире. Проверять всегда.
- `this.mc()` — `Minecraft` целиком; вызывать игру напрямую можно.
- `this.info("...")` — строка в чат (только клиенту). `this.client()` — менеджеры (друзья: `client().relations().isFriend(name)`).
- **3D** (в `Render3D`): `renderer.box(minX, minY, minZ, maxX, maxY, maxZ, strokeArgb, strokeWidth, fillArgb, throughWalls)`,
  `renderer.line(x1, y1, z1, x2, y2, z2, argb, width, throughWalls)`, положение камеры — `cameraX()`…
- **Много боксов** (Xray, Search, хранилища — больше нескольких сотен): `BoxBatch` в поле модуля, перестраивать
  (`clear()`, `add(minX, minY, minZ, maxX, maxY, maxZ, strokeArgb, fillArgb)`) только когда набор поменялся, каждый кадр —
  `renderer.boxes(batch)`. Геометрия лежит на GPU: 10 000 боксов — 5 мкс CPU на кадр. Поштучный `box` — для того,
  что движется каждый кадр (сущности).
  - `new BoxBatch(strokeWidth, throughWalls)`: толщина контура одна на весь пакет; `throughWalls` — как у `box`:
    `true` — поверх всего, `false` — блоки впереди закрывают. `batch.setThroughWalls(...)` переключает со следующего
    кадра без перестройки — под настройку модуля.
  - Проверить на своей видеокарте: `.bench boxes 10000` — 5 секунд с пакетом вокруг камеры, в чат — средний fps и худший кадр.
- **HUD-элемент** — не `Render2D`, а наследник `HudElement` (категория HUD): `measure(context)` → `{ширина, высота}` или `null`,
  если показывать нечего, и `draw(context, w, h)` в точке 0, 0. Место, якорь, масштаб, появление и редактор — от ядра;
  в редакторе (`context.preview()`) показать образец. Пример — `modules/hud/Fps.java`, подробнее — `docs/hud.md`.
- Уведомление игроку — `client().hud().notify(заголовок, текст, Toasts.Kind.INFO)`: тост, если элемент Notifications включён.
- **2D** (в `Render2D`): `roundedRect`, `roundedOutline`, `shadow`, `gradient`, `text(Type…)`. Цвета и размеры — только из
  дизайн-системы: `client().themes().current().accent()`, `Metrics.Type.LABEL.draw(...)`, см. `docs/design-system.md`.
- Сырой OpenGL запрещён: только эти хелперы (бэкенд игры может быть Vulkan).

## Файлы, секреты, сеть

Только через ядро; `java.io`/`java.nio.file`, `HttpClient`, `System.getenv` в модулях запрещены.

- **Файлы** — `this.storage()`: папка модуля в активном профиле, `.minecraft/wisteria/profiles/<профиль>/data/<id>/`;
  при смене профиля — уже другая папка. `readText`/`writeText`, `readBytes`/`writeBytes`,
  `readJson`/`writeJson`, `list()`, `delete`, `file("db/x.sqlite")` — путь для библиотек, открывающих файл сами.
  Имена — относительные (`cache/r.0.0.json`), `..` и абсолютные пути отклоняются. Запись атомарная.
  Крупное — не на клиентском потоке. `export(name, bytes)` кладёт выгрузку для пользователя в `.minecraft/wisteria/exports/`.
- **Секреты** — `this.secret("TELEGRAM_BOT_TOKEN")` → `Optional<Secret>` из `.minecraft/wisteria/.env` (`ИМЯ=значение`;
  в профиле, кроме `default`, — `ИМЯ@профиль`, без подстановки чужого).
  Значение модулю не выдаётся: `Secret` печатается как `****`, подставляется в запрос сам. Всё, что клиент пишет в чат
  и лог, чистится от значений секретов.
- **Сеть** — `this.send(Net.Request.post("https://api.telegram.org/bot{token}/sendMessage").secret("token", token).json(body))`
  → `CompletableFuture<Net.Response>`, завершается **не на клиентском потоке**: обратно — `thenAcceptAsync(r -> ..., this.platform()::execute)`.
  Только HTTPS и только хосты, которые пользователь разрешил командой `.net allow <хост>` (по умолчанию — никакие).
  Таймаут 10 с, 2 повтора при сетевой ошибке, 5xx и 429; после 3 провалов подряд сеть модуля выключается на 5 минут
  с сообщением в чат. Пока в профиле включён прокси, сеть клиента выключена целиком (`docs/proxy.md`).

**Исключения** — кому что можно (`io.Permission`, меняется только задачей на ядро вместе с этим списком).
Остальным модулям вызов бросает `SecurityException`.

| Разрешение | Кому | Что |
|---|---|---|
| `NET` | `notifier` | исходящие HTTPS-запросы на разрешённые хосты |
| `SECRETS` | `notifier` | чтение `.env` |
| `WRITE_OUTSIDE` | `waypoints` | `storage().writeOutside(path, bytes)` — экспорт вейпоинтов в выбранный файл, только по явному действию пользователя |

## Логика отдельно от игры

Решение — «ломать ли этот блок», «какой слот взять», «что подсветить» — пишется **чистой функцией над простыми
данными**: числа, строки, записи, свои enum; без `mc()`, `player()`, `level()` и объектов игры. Модуль только
собирает данные из игры, вызывает функцию и применяет результат. Так логика проверяется модульным тестом,
где `player()` — `null`. Пример — `TemplateModule.ring(...)` и `decisionLogicIsTestedWithoutTheGame`.

## Миксины

Класс в `dev.wisteria.mixin.<имя>`, строка в `wisteria.mixins.json`. Миксин ничего не решает сам, а спрашивает модуль —
по классу, не по строке:

```java
Nuker nuker = Wisteria.client().modules().get(Nuker.class);   // null, если не зарегистрирован
if (nuker != null && nuker.isEnabled()) { ... }                // или modules().isEnabled(Nuker.class)
```

## Правила

- Перенесённая логика: в javadoc класса — откуда и под какой лицензией («Adapted from Wurst7 (GPL-3.0), …»). Не копировать вслепую.
- Обработчик, который бросил исключение, выключает свой модуль и пишет об этом в чат. Игра не падает, но это баг.
- Файлы, секреты и сеть — только как выше; исключения — только по таблице.

## Тесты

**Модульный** (без игры, секунды): копия `src/test/java/dev/wisteria/modules/_template/TemplateModuleTest.java`.
`FakePlatform` — игра-заглушка: `typeInChat(".nuker")`, `tick(n)`, `press(Key.G, false)`, `stop()` и новый `Client` над тем же
каталогом — это перезапуск. `player()` в нём `null`, поэтому тестируется то, что от игры не зависит: включение,
настройки, сохранение, своя логика.

**В игре** — копия `src/gametest/java/dev/wisteria/gate/modules/FullBrightGameTest.java`, наследник `ModuleGameTest`:
даётся свежий мир, клиент и `GameTestKit` — `chat(context, ".nuker")`, `world.getServer().runCommand("setblock ...")`,
`grabLastFrame(...)` с проверкой пикселей, `click(...)` настоящей мышью, `check("что", условие)`.

```bash
./gradlew test
```

```bash
./gradlew runClientGameTest
```

## Готово, когда

1. Модуль включается из ClickGUI и командой, настройки переживают перезапуск (модульный тест).
2. Автотест в игре зелёный вместе со всем шлюзом.
3. Для активного модуля — пустая строка в `docs/grim-matrix.md`; заполняет владелец после живого теста.

## Стабильность

Всё перечисленное здесь — замороженный API: `Module` и его хелперы, классы настроек, `Events`, `Renderer2D`/`Renderer3D`,
`BoxBatch`, `HudElement`, `HudContext`, `Toasts`, новые типы настроек, `Storage` (папка профиля), `Secret`, `Net`, `Permission`, `GameTestKit`, `ModuleGameTest`, `FakePlatform`. Изменения в них — только отдельной задачей на ядро, с обновлением этого файла.
