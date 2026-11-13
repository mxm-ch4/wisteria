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
Описание — подсказка в GUI. Категории: `RENDER`, `WORLD`, `PLAYER`, `MOVEMENT`, `COMBAT`, `MISC`.
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

Реакция на изменение — `setting.onChange(value -> ...)`.
**Дефолты — в пределах Grim.** Агрессивное — отдельный `BoolSetting`, по умолчанию `false`, описание начинается с «Experimental:».

## События

| Событие | Когда | Поток |
|---|---|---|
| `Events.TickStart` | каждый тик, до тика игры | клиентский |
| `Events.Tick` | каждый тик (20/с), после тика игры | клиентский |
| `Events.Render2D` | каждый кадр HUD: `renderer()`, `tickDelta()` | клиентский |
| `Events.Render3D` | каждый кадр в мире: `renderer()`, `tickDelta()` | клиентский |
| `Events.KeyInput` | клавиша: `key()`, `action()`, `inScreen()` | клиентский |
| `Events.PacketSend` | пакет уходит: `packet()`, `name()`, `cancel()` | сетевой или клиентский |
| `Events.PacketReceive` | пакет пришёл: `packet()`, `name()`, `cancel()` | **сетевой** |
| `Events.Join`, `Events.Leave` | вход в мир и выход: сбросить состояние | клиентский |

`packet()` — настоящий объект игры: `if (event.packet() instanceof ServerboundMovePlayerPacket move) ...`.
С сетевого потока игру не трогать: `this.platform().execute(() -> ...)`.

## Доступ к игре

- `this.player()`, `this.level()` — игрок и мир или `null`, если не в мире. Проверять всегда.
- `this.mc()` — `Minecraft` целиком; вызывать игру напрямую можно.
- `this.info("...")` — строка в чат (только клиенту). `this.client()` — менеджеры (друзья: `client().relations().isFriend(name)`).
- **3D** (в `Render3D`): `renderer.box(minX, minY, minZ, maxX, maxY, maxZ, strokeArgb, strokeWidth, fillArgb, throughWalls)`,
  `renderer.line(x1, y1, z1, x2, y2, z2, argb, width, throughWalls)`, положение камеры — `cameraX()`…
- **2D** (в `Render2D`): `roundedRect`, `roundedOutline`, `shadow`, `gradient`, `text(Type…)`. Цвета и размеры — только из
  дизайн-системы: `client().themes().current().accent()`, `Metrics.Type.LABEL.draw(...)`, см. `docs/design-system.md`.
- Сырой OpenGL запрещён: только эти хелперы (бэкенд игры может быть Vulkan).

## Миксины

Класс в `dev.wisteria.mixin.<имя>`, строка в `wisteria.mixins.json`. Миксин ничего не решает сам, а спрашивает модуль:

```java
Module nuker = Wisteria.client().modules().get("nuker");
if (nuker != null && nuker.isEnabled()) { ... }
```

## Правила

- Перенесённая логика: в javadoc класса — откуда и под какой лицензией («Adapted from Wurst7 (GPL-3.0), …»). Не копировать вслепую.
- Обработчик, который бросил исключение, выключает свой модуль и пишет об этом в чат. Игра не падает, но это баг.
- Никаких токенов, сетевых запросов наружу, записи файлов вне `.minecraft/wisteria/`.

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
`GameTestKit`, `ModuleGameTest`, `FakePlatform`. Изменения в них — только отдельной задачей на ядро, с обновлением этого файла.
