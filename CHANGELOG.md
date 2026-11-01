# Changelog

**English** | [简体中文](CHANGELOG.zh-CN.md)

Major features and fixes in GestureKeys, listed from newest to oldest.

## 1.2.0 — 2026-11-01

### Added

- Added ordered action sequences with game actions, commands, and waits; per-step gaps and hold durations use seconds in 0.05-second increments.
- Added draft sequence/step editors with add/remove/reorder controls and pagination. Legacy single-action bindings continue as one-step sequences.
- Added whole-sequence validation, replacement by a new gesture, and immediate cancellation on screens, focus loss, or leaving the world.
- Added client-first command routing: other commands use the normal signed server-command interface, including `/help` and `/msg`.

### Fixed

- Removed the local-only restriction that rejected vanilla server commands.
- Clean up unconsumed simulated clicks when cancelling without removing tracked physical clicks.
- Keep later steps running after asynchronous server command errors; local failures still abort.

### Changed

- Updated both NeoForge and Fabric editions to **1.2.0**, retaining Minecraft **1.21.1** and each loader's existing input integration.
- Retained the 1.1.0 key-subclass, physical-input overlap, scan-code, and toggle compatibility fixes.
- Servers check command syntax and permissions. Selectors such as `@s` require the same permissions as normal chat.

### Verification

- Both editions passed regular builds and **34 unit tests each**.
- NeoForge: **94 client checks**; Fabric: **88 client checks**, including real integrated-server `/help`, `/msg`, offhand sequences, cancellation, and input compatibility.
- These checks use development clients and integrated servers. Multiplayer plugin combinations and online account signing policies were not separately tested.

## 1.1.0 — 2026-10-31

### Fixed

- Fixed game actions from some mods appearing in the action picker but failing to execute through gestures.
  The selected `KeyMapping` now receives press and release callbacks, including custom subclasses.
- Fixed Mekanism's chest and feet mode switches failing to execute on NeoForge.
- Improved timed-hold expiry, cancellation when opening a screen or losing focus,
  and coordination between physical input and gesture-held states.
- Preserved vanilla toggle-key behavior: another gesture can toggle the action off,
  and physical presses can still toggle it during a gesture hold.
- Added physical scan-code state tracking to avoid releasing a key the player is still holding
  when a gesture ends.

### Changed

- Updated both NeoForge and Fabric editions to **1.1.0**, continuing to support Minecraft **1.21.1**.
- Ported selected-key press/release dispatch, timed holds, scan-code handling,
  and toggle-key compatibility fixes to Fabric.
- On NeoForge, gestures execute the selected action without requiring its bound Ctrl, Shift,
  or Alt modifier to be held. The action's conflict context, saved binding,
  and gameplay state such as crouching are preserved.
- Actions remain bound by name and can have no physical key assigned.
  Other actions sharing the physical key are not triggered.

### Verification

- Both editions passed regular builds and **26 unit tests each**.
- The NeoForge client passed **57 checks**, including actual Mekanism server-side mode changes,
  synchronization back to the client, and repeated triggering.
- The Fabric client passed **45 checks**, covering custom key subclasses, clicks, holds,
  shared-key isolation, focus-loss release, and toggle keys.
- Mekanism was tested with NeoForge **21.1.234** and Mekanism **10.7.19.85**.
  Fabric was tested with generic custom key subclasses; the NeoForge Mekanism JAR was not loaded there.

### Compatibility

- Mods that listen directly to GLFW or raw keyboard events, or implement their own chord checks,
  may still need separate integration. Fabric does not include the NeoForge-specific modifier extension.

## 1.0.0 — 2026-10-27

### Added

- Introduced a client-side mouse gesture mod for Minecraft 1.21.1, with NeoForge and Fabric editions.
- Added multi-stroke gestures, 1–8 samples per gesture, and up to 128 gestures.
- Added bindings for vanilla and modded game actions, with single clicks and timed holds of 1–200 ticks.
- Added local client-command execution and screens for settings, recording, editing, and recognition tests.
- Added nine glass styles, independent interface and trail colors, and live appearance previews.
- Added mouse-wheel paging for gesture and action lists, action search, and Chinese and English interfaces.
- Added an optional Mod Menu settings entry for Fabric.
