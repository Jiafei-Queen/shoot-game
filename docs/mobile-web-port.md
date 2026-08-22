# 移动端 / Web 端移植说明

> 本文档记录 ShootGame 从纯桌面(lwjgl3)扩展到 Android 与 Web(GWT)的
> 实现要点、构建命令与真机测试清单,供后续开发与维护参考。

## 1. 新增模块

| 模块 | 平台 | 启动类 | 说明 |
|---|---|---|---|
| `android/` | Android(手机/Pad) | `org.jiafeiown.shootgame.android.AndroidLauncher` | AGP 9.3.1, compileSdk 36, minSdk 26, 锁竖屏 + 沉浸式 |
| `html/` | Web(浏览器) | `org.jiafeiown.shootgame.html.HtmlLauncher` | GWT 2.11(gdx-backend-gwt 1.14.2), 产物为静态 war |

核心玩法逻辑全部在 `core/`,三端共享,无平台分支代码。

## 2. 输入映射(核心改动)

- **开枪**:桌面按住 `SPACE`;移动/Web 按住屏幕任意处(排除暂停按钮区域)。
  单击 = 单发,长按 = 连发,与 SPACE 的 `fireCooldown` 逻辑完全一致。
  实现见 `GameWorld.fireHeld()` / `handleInput()`。
- **暂停**:右上角暂停按钮(点击/触摸)与 `ESC` 走完全相同的
  `rounds.pause()` 慢动作过渡流程。按钮命中测试见
  `GameWorld.pauseButtonHit()` + `WorldRenderer.pauseButtonAt()`。
- **游戏结束重开**:桌面按 `R`;触屏点击任意处(移动/Web 没有 R 键,
  否则会卡死在结算页)。
- 平台感知文案(`GameWorld.isTouchDevice()`):HUD 底部提示、
  暂停页提示、结算页提示按桌面/触屏分别显示。

## 3. 屏幕适配

- `FitViewport(800×1280)`(原有)即保证场景大小/比例在任意手机、Pad、
  浏览器窗口下完全一致:等比缩放 + letterbox,桌面窗口 600×960 恰好是
  同比例(5:8),因此桌面无黑边。
- Android 锁竖屏(`screenOrientation="portrait"`),沉浸式全屏
  (`useImmersiveMode`),Pad 竖屏下同样 letterbox 适配。
- Web 端 `GwtApplicationConfiguration` 无参构造 = 自适应窗口大小,
  浏览器 resize 自动触发 `viewport.update`。

## 4. 构建与运行

```bash
# Android:APK 输出到 android/build/outputs/apk/debug/
./gradlew :android:assembleDebug

# Web:GWT 编译(首次较慢,几分钟),产物在 html/build/gwt/war/
./gradlew :html:gwtCompile

# 打包 Web 产物为 zip
./gradlew :html:gwtDist

# 本地预览 Web(任意静态服务器,无需后端)
cd html/build/gwt/war && python3 -m http.server 8080
# 浏览器打开 http://localhost:8080
```

## 5. 实现细节与坑

### 5.1 Android 原生库(natives)

libGDX 的 Android natives(`gdx-platform` 的 `natives-armeabi-v7a` 等)是
**`.so` 在 jar 根目录**的构件,AGP 不会自动打包。必须用官方模板的做法:
`configurations { natives }` + `copyAndroidNatives` 任务解包到
`build/gdx-natives/<abi>/`,再让 `jniLibs.srcDirs` 指向它。缺了这步会在
启动时抛 `SharedLibraryLoadRuntimeException: Couldn't load shared library 'gdx'`。

### 5.2 minSdk 26 的原因

`log4j-core` 使用 `MethodHandle.invoke/invokeExact`,D8 要求 API ≥ 26。
若需支持 Android 7.x(API 24/25),需去掉 Android 端 log4j-core(日志退化为
log4j-api 空实现)。

### 5.3 GWT 不能编译 log4j

GWT 从源码编译,log4j-api 无法翻译。`html/` 模块用 GWT super-source 提供
`org.apache.logging.log4j.LogManager/Logger` 两个最小替身(输出到浏览器
console),桌面/Android 仍用真实 log4j2。替身位于:
`html/src/main/java/org/jiafeiown/shootgame/html/super/org/apache/logging/log4j/`

### 5.4 GWT 源码路径

GWT 只翻译"模块源码路径"内的包,且 `<source path>` 不允许 `../` 向上遍历。
因此:
- `core/` 自带 GWT 库模块 `core/src/main/java/org/jiafeiown/shootgame/ShootGame.gwt.xml`
  (`<source path="" />` 声明本包可翻译);
- `html` 的 `HtmlLauncher.gwt.xml` 用 `<inherits name='org.jiafeiown.shootgame.ShootGame'/>`
  引入,`gdx.assetpath` 相对 GWT 编译器工作目录(`build/gwt/war`,上溯 4 级 = 项目根 `assets/`)。

### 5.5 GWT 不支持 `String.format`

GWT 2.11 的 Java 模拟层没有 `String.format`。游戏结束页的计时格式化已改为
手写补零(`WorldRenderer`)。

### 5.6 构建时网络

本机网络对 Java/Gradle 的 TLS 握手偶发断连(curl 正常),已在
`gradle.properties` 加大超时与重试(`org.gradle.internal.http.*` /
`org.gradle.internal.repository.max.retries`)。另外 `subprojects` 的
repositories 必须包含 `google()` —— AndroidX 等依赖只在 Google Maven,
不在 Maven Central。

## 6. 真机/浏览器测试清单(需要人工确认画面)

- [ ] 手机:按住屏幕开枪,节奏与桌面 SPACE 一致(单击单发/长按连发)
- [ ] 手机:右上角暂停按钮 → 进入暂停页(慢动作过渡),点 CONTINUE 恢复,
      END GAME 结算、RESTART ROUND 重开
- [ ] 手机:游戏结束页点击任意处重开
- [ ] Pad:竖屏下场景比例与手机一致(letterbox 黑边,场景不变形)
- [ ] 浏览器:按住/点击画面开枪;右上角暂停按钮;窗口缩放时场景始终
      等比完整可见
- [ ] 桌面回归:SPACE 开枪、ESC 暂停、R 重开均正常(未受影响)
