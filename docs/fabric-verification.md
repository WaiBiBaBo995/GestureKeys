# Fabric 1.21.1 verification / 验证记录

## 1.2.0 — 2026-11-01

- Minecraft 1.21.1 / Fabric Loader 0.16.14 / Fabric API 0.116.17+1.21.1 / Java 21.0.11 / Mod Menu 11.0.3.
- **34 unit tests** and **88 runtime checks** passed. 两端共享的序列/config/UI/测试已同步，Fabric 保留原生客户端指令、按键和 ScreenEvents API。
- Checks cover ordered hotbar slot 2 → offhand swap → vanilla offhand use, editor drafts and timing, cancellation/replacement, pending-click ownership, physical input, toggles, and scan codes.
- `/help` returns server help; `/msg @s 111` returns self-message feedback. The test confirms that `/msg` has signable arguments and uses the normal command-send path.
- 本地同名命令优先；没有公开给客户端的服务端命令仍可执行；服务器失败/未知命令反馈后，后续步骤继续。打开界面仍取消序列。
- Native final `Options.keyMappings` is preserved. Only the development harness temporarily replaces/restores reserved array entries for synthetic-key checks.

```powershell
.\gradlew.bat test build --offline
.\gradlew.bat runClient -PlaunchCheck -PlaunchCheckDir=build/fabric-120-check --offline
```

Evidence / 原始结果：[88 checks](fabric/sequence-command-1.2.0-checks.txt)、[client log](fabric/sequence-command-1.2.0-client.log)。

![Fabric sequence editor](fabric/sequence-editor-1.2.0.png)

![Fabric compact step editor](fabric/sequence-step-compact-1.2.0.png)

Tests used the integrated server with selector permission, not a multiplayer plugin server or hardware Alt input. Fabric does not gain NeoForge's modifier-specific mixin.

## 1.1.0 — 2026-10-31

Version 1.1.0 ports the selected-mapping press/release dispatch, timed holds, physical-input
overlap, scan-code state tracking, and native toggle behavior from the NeoForge fix.
Fabric client commands, registration, shaders, and optional Mod Menu integration are preserved.

1.1.0 同步了 NeoForge 端的目标按键按下/释放派发、定时按住、物理输入重叠、扫描码状态跟踪和原版切换键行为。
保留 Fabric 本地指令、注册、Shader 与可选 Mod Menu 接入。

- `./gradlew.bat --offline build`: 26 unit tests passed / 26 项单元测试通过。
- `./gradlew.bat --offline -PlaunchCheck runClient`: 45 runtime checks passed / 45 项运行检查通过。
- The runtime check confirms GestureKeys 1.1.0 on Minecraft 1.21.1, Fabric Loader 0.16.14,
  Fabric API 0.116.17+1.21.1, Mod Menu 11.0.3, Java 21.0.11 and RTX 4060 Laptop.
- New coverage includes custom subclasses that bypass the parent `isDown`, unbound actions,
  exact click counts and deadlines, repeated gestures, shared-key isolation, focus cancellation,
  toggle keys, and physical scan-code holds/releases.
- 新增检查覆盖绕过父类 `isDown` 的自定义子类、未绑定动作、点击计数与时限、重复触发、同键隔离、失焦取消、
  原版切换键，以及扫描码物理按住/释放。

Evidence / 原始结果：[45 checks](fabric/input-compatibility-1.1.0-checks.txt).

Fabric has no NeoForge `KeyModifier` API, so the NeoForge-only modifier extension Mixin is not
included. Mods with their own chord or raw-input checks may need separate adapters. The provided
Mekanism JAR targets NeoForge and was not loaded in Fabric. Authentication and Mod Menu update
requests timed out in this test environment; all in-game checks completed successfully.

Fabric 没有 NeoForge 的 `KeyModifier` API，因此不包含其专用修饰键扩展 Mixin。
自行检查组合键或原始输入的模组仍可能需要单独适配。用户提供的 Mekanism JAR 为 NeoForge 版，未在 Fabric 加载。
本次身份认证及 Mod Menu 更新检查请求超时，游戏内检查全部完成。

## Historical port / 历史移植记录

Date / 日期：2026-10-27

| Component / 项目 | Verified environment / 验证环境 |
| --- | --- |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.116.17+1.21.1 |
| Mod Menu | 11.0.3 |
| Java | GraalVM JDK 21.0.11 |
| OS / 系统 | Windows |
| GPU | NVIDIA GeForce RTX 4060 Laptop GPU |
| Build / 构建 | Fabric Loom 1.14.10, Gradle Wrapper 9.2.1, Mojang mappings |

## Results / 结果

The Fabric client launched successfully and completed **28 runtime checks**. The development
harness opened real screens, rendered frames, reloaded resources, and entered a fresh flat
single-player world. The checks use Minecraft and mod APIs; they do not simulate physical Alt
input. Unit tests were not run for this port.

Fabric 客户端实际启动成功，完成 **28 项运行检查**。开发检查程序打开真实界面、渲染画面、
重载资源，并进入新建的超平坦单人世界。检查通过游戏和模组 API 驱动，未模拟真实硬件 Alt 输入；
此次移植未运行单元测试。

- Client entry point, activation-key registration and binding lookup.
  客户端入口、激活键注册与绑定读取。
- Settings, appearance, color picker, action picker wheel handler, and Mod Menu settings factory.
  设置、外观、调色盘、动作选择滚轮处理与 Mod Menu 设置入口。
- Nine glass styles plus Off, antialiased trail shaders, OpenGL state, and resource reload.
  九种玻璃风格及关闭、抗锯齿轨迹 Shader、OpenGL 状态与资源重载。
- In-world inventory action, timed jump hold, synthetic key-state mixin, and release on opening a screen.
  世界内背包动作、定时跳跃长按、模拟按键状态 Mixin 与打开界面时释放。
- Local Fabric client command opens settings through the action executor.
  通过动作执行器调用 Fabric 本地客户端指令并打开设置。

Raw evidence / 原始记录：[28 checks](fabric/checks.txt) · [environment](fabric/environment.txt).

## Captures / 实际截图

Captured at 854 × 480, GUI scale 2. The palette, crystal trail, preferences, and action-page footer
were visually inspected. The gap in the crystal trail intentionally represents a pen lift.

截图分辨率 854 × 480、GUI 缩放 2。已目视检查调色盘、水晶轨迹、外观滑条和动作页脚；
水晶轨迹中央的间隙用于检查抬笔后的分段。

![Fabric appearance sliders / Fabric 外观滑条](fabric/preferences.png)

![Fabric trail palette / Fabric 轨迹调色盘](fabric/palette.png)

![Fabric crystal glass and separate strokes / Fabric 水晶玻璃与分段轨迹](fabric/crystal.png)

![Fabric action pagination / Fabric 动作分页](fabric/actions.png)

![Fabric settings in a world / Fabric 世界内设置](fabric/world-settings.png)

## Reproduce / 复现

Use Java 21 and the project wrapper / 使用 Java 21 和项目 Wrapper：

```powershell
.\gradlew.bat -PlaunchCheck runClient
.\gradlew.bat clean assemble -x test
```

The launch check uses the isolated run-fabric-check directory. Evidence is written to
build/fabric-launch-check. Archive it before clean. A FAILED.txt file means the check failed,
even if Gradle exits successfully. Regular builds exclude the development checker; the old
NeoForge src/smoke harness is also excluded.

启动检查使用独立的 run-fabric-check 目录，输出位于 build/fabric-launch-check；执行 clean
前应保存记录。若存在 FAILED.txt，即使 Gradle 返回成功，也应判定检查失败。普通构建不包含
开发检查代码；原 NeoForge 的 src/smoke 检查代码同样不参与 Fabric 构建。

## Scope / 验证边界

Physical Alt release, OS focus changes, multiplayer, and third-party rendering mods still require
manual regression in the intended instance. Mod Menu was installed during this run. The wheel
handler was invoked; a complete pagination boundary regression was not repeated. Style and
palette frames render correctly; the full prior save/cancel and live-drag regression was not
repeated. No Fabric performance benchmark was collected. Historical NeoForge measurements
in [verification.md](verification.md) do not establish Fabric timing or compatibility.

真实 Alt 松开、操作系统切换焦点、多人环境及第三方渲染模组仍需在目标实例中手动回归。
此次启动安装了 Mod Menu。已调用滚轮处理，但未重复完整分页边界回归。风格与调色盘画面正常，
未重复此前完整的保存/取消和实时拖动回归。未采集 Fabric 性能基准；
[verification.md](verification.md) 中的历史 NeoForge 数据不代表 Fabric 性能或兼容性。
