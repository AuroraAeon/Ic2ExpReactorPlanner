# IC2 Experimental Reactor Planner · GTNH 2.9.0

Minecraft 工业时代 2 反应堆布局编辑器与离线模拟器。支持 GT New Horizons 2.9.0 的 EU 反应堆、IC2 流体反应堆和外部真空冷冻机换件模型。保留旧版布局导入。

## 构建与运行 / Build and launch

需要 JDK 17 或更新版本。本分支使用 Gradle Wrapper，首次构建会下载 Gradle 和测试依赖。

```powershell
.\gradlew.bat clean build
java -jar build/libs/Ic2ExpReactorPlanner-2.5.0-gtnh.1.jar
```

On macOS or Linux, use `./gradlew clean build`. The executable JAR is in `build/libs/` and the ZIP with launcher scripts is in `build/distributions/`. The app selects English or Simplified Chinese from the JVM locale. Use `--lang=en` or `--lang=zh-CN` after the JAR name to select the language.

Windows 便携应用可通过 JDK 的 `jpackage` 打包，内含 Java 运行时。

```powershell
jpackage --type app-image --name Ic2ExpReactorPlanner --app-version 2.5.0 --input build/libs --main-jar Ic2ExpReactorPlanner-2.5.0-gtnh.1.jar --dest build/windows
```

## 运行一个完整周期

1. 打开 [被动冷却示例](examples/gtnh-2.9.0-thorium-11-passive.txt)，复制文件中的 `erp=` 布局代码。
2. 粘贴到布局代码输入区并导入。确认规则配置为 **GTNH 2.9.0**、扩容仓为 **2**。
3. 开始模拟，检查第一个元件失效、最高堆温、燃料耗尽时间和产物。这个示例装有 11 根四联钍棒，离线模型跑满 49,999 次更新，峰值堆温为 0，消耗完的 11 根燃料棒可热力离心为 22 个镥粉、44 个钍粉和 66 个铁粉。

## 文档 / Documentation

- [操作指南 / User guide](docs/user-guide.md)
- [GTNH 2.9.0 版本与源码审计](docs/gtnh-source-audit.md)
- [钍燃料产物与真空冷却](docs/thorium-and-vacuum.md)
- [架构、布局兼容和测试](docs/engineering.md)
- [性能基准与重跑方法](docs/performance.md)

Upstream project by [MauveCloud](https://github.com/MauveCloud/Ic2ExpReactorPlanner).
