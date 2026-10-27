# GestureKeys

**Turn everyday actions into mouse gestures.** A client-side Minecraft mod with multi-stroke
recognition, glass interfaces, and live appearance previews.

**English** | [简体中文](README.zh-CN.md)

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge 21.1.172+](https://img.shields.io/badge/NeoForge-21.1.172%2B-E99035)
![Java 21](https://img.shields.io/badge/Java-21-437DB0)
![Client-side](https://img.shields.io/badge/Side-Client-80D9EA)
[![Apache License 2.0](https://img.shields.io/badge/License-Apache--2.0-437DB0)](LICENSE)

- **Draw an action**: bind gestures to vanilla or modded game actions, or run local client commands.
- **Use multiple strokes**: lift the mouse button and continue, with multiple samples per gesture.
- **Choose from nine glass styles**: liquid glass, mica, crystal, and more, with independent
  interface and trail colors.
- **Preview as you adjust**: see style, strength, and color changes immediately; save to apply
  or cancel to discard.

![In-game gesture overlay with a drawing trail, settings gear, and recognition result](docs/gesture-overlay.png)

Screenshots show the Simplified Chinese interface. English UI text is also included in the mod.

[Installation](#installation) · [Quick start](#quick-start) ·
[Gestures and actions](#gestures-and-actions) · [Appearance](#appearance) ·
[FAQ](#faq) · [Development and verification](#development-and-verification)

## Installation

| Component | Requirement |
| --- | --- |
| Minecraft | **Java Edition 1.21.1** |
| Mod loader | **NeoForge 21.1.172 or a newer 21.1 release** |
| Java | **21** |
| Install location | The client instance's `mods` folder |

Place `gesturekeys-1.0.0.jar` in the `mods` folder and launch the game.
No server installation is required. If you build from source, the installable JAR is generated
in `build/libs/`; files ending in `-sources.jar` contain source code.

> **First time?** Open settings and create and save a gesture before drawing it in the game.

## Quick start

### Create your first gesture

1. Enter a world, hold **Left Alt**, and click the gear in the lower-left corner to open settings.
   You can then release Alt.
2. Select **New gesture** and enter a name, such as “Open inventory.”
3. Choose the corresponding **Game action**, record a sample, and select **Finish recording**.
4. Use **Test recognition** to check the result, save the gesture, and return to the game.
5. Hold **Left Alt** again, draw the gesture with the left mouse button, and release Alt to execute.

You can also open settings from the mod list's configuration entry or with `/gesturekeys config`.

### Drawing controls

| Input | Effect |
| --- | --- |
| Hold Left Alt | Open the gesture overlay and show the mouse cursor |
| Hold and drag the left mouse button | Draw the current stroke |
| Release the left button, then draw again | Add another stroke while keeping the existing trail |
| Right mouse button | Clear all strokes and start over |
| Release Alt | Recognize the gesture and execute its bound action |
| Esc | Cancel the current drawing |

The bottom panel shows the candidate gesture, confidence, action, or rejection reason while drawing.
**The action runs only when you release Alt.** Accepted gestures execute without an extra success
notification. Rejected gestures briefly show “Not recognized” or “Gesture conflict” above the hotbar.
Releasing Alt without drawing, or after clearing the drawing, produces no notification.

Change the activation key in **Options → Controls → Key Binds → GestureKeys**.
The gesture overlay opens only during normal gameplay.

## Gestures and actions

Each gesture supports **1–8 samples**, with up to **128 gestures** in total.
Creating and editing use drafts; canceling does not change saved gestures.

Both the gesture list and game-action picker support mouse-wheel paging: scroll down for the next
page and up for the previous page. Pages do not wrap at either end.
The footer shows the item count and **Page n / x**; an empty list shows **Page 1 / 1**.
Paging through actions preserves the search query; changing the query returns to the first page.

### Available action types

| Action type | Behavior |
| --- | --- |
| Game action: click once | Trigger the action once and provide a pressed state for 1 tick |
| Game action: timed hold | Keep the action pressed for **1–200 ticks**, defaulting to 1 tick |
| Client command | Run a locally registered client command, with or without a leading `/` |

Search for game actions by name. Vanilla actions and modded actions registered through `KeyMapping`
are supported, including actions without a physical key binding. Changing the physical binding
in the game's controls does not require reconfiguring the gesture.

Normally, **20 ticks are about 1 second**; client stalls can affect the actual duration.
Triggering the same action again restarts its timer. Simulated pressed states are released when
you enter another screen, switch away from the game window, or leave the world.

You can use `/gesturekeys` to try a client command. Unknown commands, invalid arguments, and
execution failures display an error. **Commands are not automatically forwarded to the server.**
The command itself may produce its own feedback.

### Tune recognition

Use the sliders in **Recognition & appearance**, or adjust them with the arrow keys in
**1-percentage-point** steps.

| Setting | Default | Range | Effect |
| --- | --- | --- | --- |
| Minimum confidence | 78% | 60–95% | Lower is more permissive; higher is stricter |
| Minimum candidate gap | 6% | 2–15% | Higher values reject similar candidates more readily |

Start with the defaults, record additional naturally drawn samples, and make different gestures
visually distinct. The editor warns when a sample is too similar to another gesture.

<details>
<summary>Which drawing differences does recognition ignore?</summary>

Recognition ignores position, overall size, drawing speed, stroke order, starting point, drawing
direction, and how the same path is split into strokes. Rotation, mirroring, and aspect-ratio
differences still help distinguish shapes. Geometrically identical shapes cannot be distinguished
by shape alone.

Trail smoothing affects display only. Recognition and saved samples use the original coordinates
and are not changed by appearance settings.

</details>

## Appearance

Open **Recognition & appearance** in settings to change the glass style, blur and refraction
strength, reveal animation, and colors.

### Nine glass styles

Click **Glass style** below the strength slider to cycle through the styles, including **Off**.

| Liquid glass | Frosted glass | Glassmorphism |
| --- | --- | --- |
| Acrylic | Mica | Smoked glass |
| Gradient glass | Aurora glass | Crystal glass |

![Nine glass styles in-game, arranged row by row to match the table above](docs/materials.png)

Each style has its own blur, opacity, refraction, grain, highlights, and color effects.
Adjust **Glass blur & refraction** from **50–150%** in **1-percentage-point** steps.

### Independent interface and trail colors

Both color pickers offer **12 presets, an HSV palette, and HEX input**.

| Color setting | Applies to |
| --- | --- |
| Interface color | Glass surfaces, control highlights, and accents; text and status stay readable |
| Trail color | In-game drawing, sample recording, recognition tests, and gesture thumbnails |

![Trail color picker with HSV selection, presets, HEX input, and a live curve preview](docs/palette-trail.png)

Style, strength, and color changes **preview immediately**, without saving first.

| Screen | Done or Save | Cancel or Esc |
| --- | --- | --- |
| Color picker | Return the chosen color to the appearance draft | Discard this color edit |
| Recognition & appearance | Save all draft settings to the configuration | Discard all unsaved changes |

Canceling the color picker restores the color from before it was opened.
Trails use an antialiased shader, rounded ends and joins, and lightweight smoothing, while
preserving sharp corners, endpoints, and gaps between strokes. If a shader fails to load or render,
glass controls fall back to translucent rounded controls; trails also have a geometry fallback.
More screenshots are available in the [verification record](docs/verification.md#界面截图) (Chinese).

## FAQ

### Why do I get “Not recognized” or “Gesture conflict”?

“Not recognized” means the drawing did not meet the recognition criteria. “Gesture conflict” means
the top two candidates were too close, so the mod refused to execute either action.
Try the recognition test, record more samples, or make similar gestures more distinct before
adjusting the sliders.

### Why do some modded key actions not work?

GestureKeys triggers game actions through `KeyMapping`. Mods that listen directly to GLFW or raw
keyboard events need separate integration. Features absent from the action list cannot be bound
directly.

### Can gestures run server commands?

Command actions use NeoForge's local client-command dispatcher and do not automatically send
commands to the server.

### Does opening the interface pause the game?

The gesture overlay does not pause the world. Settings, editing, recording, and recognition-test
screens pause a single-player world when it has not been opened to LAN. Multiplayer worlds
continue running.

### What is the performance impact of the glass and trail effects?

Blur uses two half-resolution passes, trails are batched, and live color changes reuse render
targets. In a local RTX 4060 Laptop test at 1080p, the median GPU time for the mod's rendering
dropped from about **0.59 ms** before the update to about **0.25–0.35 ms** across the current styles.

This measures eight panels and one trail. **It is not total game frame time and does not guarantee
frame rates on other hardware.** Other GPUs and third-party rendering-mod combinations still
need verification in the target instance. See the [performance record](docs/verification.md#性能)
(Chinese) for the environment, long-trail results, and measurement scope.

## Configuration

The configuration is stored as UTF-8 JSON in the game instance's `config/gesturekeys.json`.

- Saving uses a temporary file and atomic replacement.
- A damaged configuration is backed up as `gesturekeys.json.broken-<timestamp>` before an empty
  configuration is used.
- Legacy configurations automatically migrate the original shader toggle state.

## Development and verification

Players can install a compiled JAR directly. Expand the section below for build commands and
verification entry points.

<details>
<summary>Development, builds, and verification</summary>

### Project details

| Item | Value |
| --- | --- |
| Author | PureNeko |
| Mod ID | `gesturekeys` |
| Package | `com.pureneko.gesturekeys` |
| Build configuration | [gradle.properties](gradle.properties) |

Use Java 21 and the Gradle Wrapper from the project root. The first build needs to download
dependencies. These examples use Windows PowerShell.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

`neo_version` selects the compile and runtime version. `neo_min_version` independently sets the
minimum version required to install the mod. For example, this runs against a newer version
without raising the minimum requirement:

```powershell
.\gradlew.bat -Pneo_version=21.1.264 runClient
```

### Automated checks

The following checks were completed as of **October 27, 2026**. Raw reports and screenshots are in
the [verification record](docs/verification.md) (Chinese). These results are from an earlier
feature version; the subsequent wheel paging, page-layout fix, and license updates were not tested.

| Check | Result |
| --- | --- |
| Unit tests | 26 passed |
| Client functionality regression checks | 32 passed |
| Appearance, preview, and rendering checks | 61 passed |

Unit tests cover multi-stroke paths, direction and scale handling, hand-drawn aspect-ratio
tolerance, rejection of similar gestures, tick timing, configuration persistence and recovery,
legacy migration, color conversion, and trail smoothing.

```powershell
# Client functionality regression checks
.\gradlew.bat -Psmoke runClient

# Appearance, live previews, and performance; includes functionality regression checks
.\gradlew.bat -PvisualSmoke runClient
```

| Flag | Separate development instance | Reports and frame captures |
| --- | --- | --- |
| `-Psmoke` | `run-smoke` | `build/smoke` |
| `-PvisualSmoke` | `run-visual-smoke` | `build/visual-smoke` and `build/smoke` |

The checks use the mod's internal input and screen APIs in a test world, then exit automatically.
Performance checks cover 854×480 and 1920×1080, trails with 80 and 4096 points, and a comparison
with the previous renderer. Each mode warms up for 35 frames before sampling 80 frames, recording
median CPU time, median GPU time, and GPU P95.

Physical Alt input, window-focus changes, multiplayer servers, other GPUs, and third-party
rendering-mod combinations still need full manual regression checks in the target instance.

### Release builds

`src/smoke` is compiled only when a check flag is specified. Before publishing, run a regular
build to exclude the check code and baseline shaders:

```powershell
.\gradlew.bat clean build
```

The installable and source JARs are generated in `build/libs/`. The `clean` task deletes reports
and screenshots under `build/`, so archive any results you want to keep beforehand.

</details>

## Author and license

Copyright 2026 PureNeko.

This project is licensed under the **[Apache License 2.0](LICENSE)**. The full license text is
included in both the installable JAR and the source JAR.
