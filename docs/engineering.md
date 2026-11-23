# 架构、兼容与验证

## 初始审计

- **`Reactor.java`** 持有固定 6×9 网格、模式和运行参数，也负责所有历史格式的解析。旧导入直接改写现有网格，遇到坏代码可能留下部分修改。模拟复制依赖重新编码，布局的部分自动化参数会在此过程中丢失。
- **`AutomationSimulator.java`** 同时负责数值更新、日志、CSV、冷却尾段、线程生命周期和 Swing 回调。循环反复扫描 54 个格子，未启用自动化时仍扫描自动化条件。异常可被吞掉并标为已完成。冷却尾段无法可靠取消。
- **`ComponentFactory.java`、`FuelRod.java`** 共有一套供不同 Minecraft 和 GregTech 版本复用的模板。原 GTNH 模式主要通过倍乘和几个额外 ID 表达。燃料能量和反射耐久受全局开关影响，燃料棒、散热器和交换器会分配临时邻接列表。源码差异详见 [GTNH 审计](gtnh-source-audit.md)。
- **`ReactorPlannerFrame.java`** 以 NetBeans 表单生成的大段固定布局为主。网格有逐格放置和配置，缺少块操作、可搜索组件面板、拖放和撤销栈。
- **构建与测试** 使用 Gradle 2.4 和陈旧的 Java 目标声明。上游已有 JUnit 声明，提交树没有自动化测试或性能基准。上游源码以 JDK 21 的 `javac --release 17` 直接构建得到性能比较的基线。

## 现在的边界

- `Reactor` 管理布局、参数和数值状态。每次运行用 `copyDesign()` 建立独立副本，保留版本和完整配置。
- `ReactorSimulation` 在运行开始收集按行排序的占用槽和燃料棒。每次更新按该顺序执行热量和发电阶段。同一步中组件的移除、热扩散、反射和槽位顺序都可能影响后续槽，因此同一堆内保持串行。
- `AutomationSimulator` 把核心接到 Swing 后台线程，经 EDT 发布进度和事件。无窗口测试可调用 `AutomationSimulator.simulate(design)`。
- `SimulationProfile` 和组件类实现针对版本的数值规则。外部换件、冷冻机和热力离心分别由 `VacuumCooling` 与 `GtnhThoriumCycle` 计算。
- 保留 Swing 的现成桌面剪贴板、Drag and Drop、跨平台打包和 EDT 调度。替换难维护的生成布局和重复网格绑定。框架迁移会增加运行时和回归范围，对与游戏源码对齐的串行热路径没有直接收益。

## 布局格式

- 读取历史十六进制、ERP Base64 revisions 0–3，以及原项目支持的 Talonius v3 代码和链接。遇到不能代表的 Talonius 槽位会显示警告。失败导入保留当前设计。
- 在默认旧版兼容配置下继续写 revision 3。旧格式的游戏版本和仓数需要在导入时由当前设置补齐。若代码含更宽布局，导入时扩展至足以容纳现有组件。
- Revision 4 保存规则配置、扩容仓数及流体转换上限，并为新增元件和 1G 热容量保留编码空间。旧版本程序读取 revision 4 需要升级。可复制旧式布局供旧版读取，版本化设置和新增组件有兼容限制。
- 外部真空冷冻机和物流配置在运行时设置。布局文件描述堆内元件，复现实验时应同时记录 [真空参数](thorium-and-vacuum.md)。

## 本地和 CI 验证

```powershell
.\gradlew.bat clean build
.\gradlew.bat benchmark
```

Gradle 生成 `build/reports/tests/test/index.html`（英文）、`build/reports/tests/testChinese/index.html`（简体中文）和逐测试 XML。CI 为 Windows 和 Linux 构建并保存 JAR、启动脚本 ZIP 和两种语言的测试结果。本地测试覆盖发布过的布局、版本化往返、错误导入、热量与发电相位、反射和元件失效、流体供给不足、镥粉配方和冷冻机换件。Windows 桌面冒烟验证包含 150% 缩放下的布局导入、填充、剪贴板、撤销重做、取消后重试和结果比较。

历史布局来自 [MauveCloud 的棋盘布局（#594）](https://forum.industrial-craft.net/thread/8966-official-new-reactors-design-thread/?postID=202982) 和 [2.4.2 流体 ERP 布局（#21）](https://forum.industrial-craft.net/thread/15605-high-output-fluid-reactors/?pageNo=2)。

## 模型边界

模拟按 20 个游戏刻处理一次堆内更新。秒数按 20 TPS 换算。累计 EU 和 HU 计入运行段，余热冷却单列。流体设置表示每次堆更新可用的冷却液及热冷却液出罐空间，不能从该数字推断供液系统的瞬时网络行为。流体模式通过热冷却液数计算 IC2 液体热交换器可回收的 HU，流体转换率和热换算不代表 GT 大型热交换器的蒸汽产量。世界方块随机损伤、TPS 下降、供电负载、Chunk 卸载、物流时序和外部红石故障未在本地世界复现。
