# Матрица версий

Версия считается поддержанной, только если на ней зелёный шлюз — `tools/gate.sh` (SPEC_AMENDMENT_2 §1).
Что шлюз проверяет и почему именно так — в [stage0-findings.md](stage0-findings.md#8-мульти-версия-и-compat-слой).

| Версия | Уровень | Шлюз OpenGL | Шлюз Vulkan | Что в compat отличается | Известное |
|---|:-:|:-:|:-:|---|---|
| 26.1.2 | Tier 2 | ✅ 2026-11-13 | нет в игре | пайплайн: uniform-ы по одному и `withVertexFormat`; событие кадра мира в `LevelRenderEvents`; экран через `Minecraft.setScreen` | заливка 3D-бокса бледнее, чем на 26.2 |
| **26.2** | **Tier 1** | ✅ 2026-11-13 | ✅ 2026-11-13 | эталон: `BindGroupLayouts.MATRICES_PROJECTION`, `LevelExtractionEvents`, `Minecraft.gui` | — |
| 26.3 | Tier 2 | ✅ 2026-11-13 | ✅ 2026-11-13 | пайплайн в `com.mojang.renderpearl`, раздельные `PROJECTION` + `DYNAMIC_TRANSFORMS`; шейдер с `layout(location)`; ввод SDL (другие коды клавиш, `isKeyDown(code)`); `getOrResolve` у звука | OpenGL под Xvfb только через EGL |

Все прогоны — на программном рендере Mesa llvmpipe в контейнере. На настоящей видеокарте не проверялось.

## Версии зависимостей

| | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|
| Fabric API | 0.155.3+26.1.2 | 0.161.0+26.2 | 0.166.2+26.3 |
| Baritone (не запускали) | v1.18.0 | v1.19.0 | v1.20.0 |
| SeedcrackerX (не запускали) | 2.16.0 | 2.16.1 | 2.16.2, пререлиз |

Общее: Java 25, Fabric Loader 0.20.1, Fabric Loom 1.19.4, Gradle 9.8.2.

## Как добавить версию

1. Скопировать ближайший каталог `compat/v26_x`, поправить `gradle.properties` (версия игры и Fabric API),
   добавить проект в `settings.gradle`.
2. `./gradlew :v26_y:genSources`, пройтись по таблице отличий в находках этапа 0, поправить версионные файлы.
   Всё, что перестало компилироваться в `compat/common`, либо выносится в версионный каталог, либо
   переписывается так, чтобы собиралось везде.
3. `tools/gate.sh 26_y OPENGL` и `tools/gate.sh 26_y VULKAN` до зелёного.
4. Добавить строку в таблицу выше с датой прогона.
