# Runtime reload rendering

This fork of [ABKQPO/ModernSplash](https://github.com/ABKQPO/ModernSplash), based on
`4cabdaa940355acd0c6a06a65b7194f50eda490b`, exposes an optional runtime renderer for
[LegacyVisualFix](https://github.com/LaplaceRungeLenz/LegacyVisualFix).

The original mod redirects Forge's startup splash to a dedicated thread using a
shared OpenGL context. `finish()` joins that thread and deletes its textures.
Calling `start()` again during a texture/shader reload is not a safe runtime API.

The fork extracts `CustomSplash.FrameRenderer` from the startup loop. Startup and
runtime both call the same drawing implementation, using the same configured
logo, colors, memory display and optional Forge animation. Runtime progress comes
from completed resource listeners/shader stages; it does not reuse startup timing.

## API v1

`gkappa.modernsplash.RuntimeSplash` has static methods:

```java
int apiVersion(); // 1
boolean begin();
void render(String title, String detail, int completed, int total);
void end();
```

- Invoke only on the client render thread after the startup worker has finished.
- `begin()` returns false if the splash is disabled or startup has not finished.
- Only one runtime session may exist. `render()` and `end()` must use its owner thread.
- Wrap all calls, including `begin()` and `end()`, in a GL state guard. The caller
  must select the framebuffer, restore shaders/texture units/matrix stacks and
  attributes, swap the display and pump events.
- Call `end()` in `finally`. A failed `begin()` cleans up its allocated textures.
- Runtime owns independent textures/font renderer. It does not replace startup
  fields, change startup history, start workers, transfer contexts or touch the
  actively reloading Minecraft texture manager.
- Texture paths and resolved day/night colors follow `config/splash.properties`
  as loaded at startup. Restart after changing that configuration.
- This API supplies frames; callers supply reload hooks, progress and transitions.

LegacyVisualFix automatically uses this API when available. Its `theme.properties`
`enabled` switch and fade durations still apply; artwork/colors use ModernSplash's
configuration. The original upstream jar lacks this API and falls back safely.

## Build

Use JDK 25 and `./gradlew build` (Windows: `gradlew.bat build`). Gradle/conventions
match the current LegacyVisualFix build. Output remains Java 8 bytecode. The
default development version is `1.0.0-runtime.1`; `VERSION` can override it.

`CustomSplash.java` and its extracted runtime support remain LGPL-2.1; the rest
retains the upstream licensing stated in `LICENSE`. Mojang artwork is unchanged.
