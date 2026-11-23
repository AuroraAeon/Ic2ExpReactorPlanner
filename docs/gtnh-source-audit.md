# GT New Horizons 2.9.0 源码适配报告

## 版本与证据

- 整合包锁定 `2.9.0`（`79f9a03b5dd9e492d28998b9dee0ea1d1822a6b1`）。核对官方 Java 17–26 Prism ZIP 内的模组文件和 `config/IC2.ini`，包内是 `gregtech-5.09.54.265.jar`、`industrialcraft-2-2.2.828-experimental.jar`、`hodgepodge-2.7.221.jar`、`IC2NuclearControl-2.7.17.jar` 与 `GTNewHorizonsCoreMod-2.9.99.jar`。该包内没有 Nuclear Horizons。 [整合包发布页](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/releases/tag/2.9.0) · [官方客户端 ZIP](https://downloads.gtnewhorizons.com/Multi_mc_downloads/GT_New_Horizons_2.9.0_Java_17-26.zip)
- GregTech 锁定 `5.09.54.265`（`bb00f3f1023e20d3cce84cf09abb442d22a3a5a5`）。源码包 SHA-256 为 `4f51884f0583890b23d0ac9b20bab2289cee7441450cfc35a1693b15f332d7c0`。组件的数据项取自 `LoaderGTBlockFluid`，回调行为取自 `ItemRadioactiveCellIC`、`ItemCoolantCellIC`、`ItemBreederCell` 和 `ItemNeutronReflector`。 [组件注册](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/loaders/preload/LoaderGTBlockFluid.java) · [燃料棒行为](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/api/items/ItemRadioactiveCellIC.java)
- IC2 的 `TileEntityNuclearReactorElectric`、`BlocksItems` 和 `ic2.core.item.reactor` 配置及行为来自正式包内 JAR 的字节码，SHA-256 为 `de1d4597972be036eccd1c3b37e9980c3c9d9cdb92f52df2bf470971873893f6`。采用 CFR 0.152 辅助阅读，Planner 仓库不分发该 JAR 或反编译文本。`IC2.ini` 的同期参数是 `nuclear = 5.0`、`generator = 1.0`、`outputModifier = 1.0`、`reactorExplosionPowerLimit = 100`。 [IC2 配置](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/blob/79f9a03b5dd9e492d28998b9dee0ea1d1822a6b1/config/IC2.ini)
- Hodgepodge `2.7.221`（`3a6e21e69781c24a0e13f1a5623f37de6e5b94b7`）对反应堆仓、槽访问及尺寸查找有修补，`synchronizeIC2Reactors=true` 使更新相位与世界时间对齐。Nuclear Control `2.7.17` 的反应堆相关代码以监控和信号控制为主。CoreMod `2.9.99` 的 `IC2Converter` 替换太阳能板。 [Hodgepodge 同步补丁](https://github.com/GTNewHorizons/Hodgepodge/blob/3a6e21e69781c24a0e13f1a5623f37de6e5b94b7/src/main/java/com/mitchej123/hodgepodge/mixins/late/ic2/sync/MixinTEReactor.java) · [整合包 Hodgepodge 配置](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/blob/79f9a03b5dd9e492d28998b9dee0ea1d1822a6b1/config/hodgepodge.cfg) · [Nuclear Control](https://github.com/GTNewHorizons/Nuclear-Control/tree/da2278ed0a2cd810e2366d81fb7a24d7de8b349e) · [CoreMod](https://github.com/GTNewHorizons/NewHorizonsCoreMod/blob/9f108160f88f7d6ba7e20d830eeee760d7198144/src/main/java/com/dreammaster/ic2/IC2Converter.java)

## 燃料参数与兼容映射

`SimulationProfile.GtnhFuel` 按稳定的 Planner 组件 ID 取参数。GTNH 中原有布局的铀、MOX、钍 ID 指向对应的 GregTech 普通反应堆燃料，和 `FissionFuelLoader` 的配方一致。老版 GT5.09 Naquadah 与 Coaxium/Cesium 仍属于传统配置。各条目依次给出物品最大耐久、单脉冲原始输出、三角数热系数和 MOX 温度系数。 [燃料配方来源](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/loaders/load/FissionFuelLoader.java)

- 铀（1–3）`20000 / 2 / 4 / 0`，MOX（4–6）`10000 / 2 / 4 / 1.5`，钍（26–28）`50000 / 0.4 / 1 / 0`。三个类别的最大耐久和常规热系数与原 Planner 的数值一致。实际单脉冲电率是原 GTNH 近似值 50 EU/t、50 EU/t、10 EU/t，区别在于异种相邻棒的脉冲归属、单精度累加和寿命边界。
- 硅岩（45–47）`100000 / 4 / 4 / 0`，其中四联的热系数为 `1`。超能硅岩（48–50）`100000 / 4 / 4 / 1.5`。这两类的基准脉冲电率从原 GTNH 近似的 50 改为 100 EU/t。四联硅岩每内部燃料单元的热系数从 4 改为 1。
- 泰伯利亚（51–53）`50000 / 2 / 2 / 0`，维持 50 EU/t 的基准电率。The Core（54）`100000 / 8 / 4 / 0`，包含 32 个内部燃料单元，基础脉冲数 17。原 Planner 将其按四联的基础脉冲 3 和估算系数处理。
- 浓缩铀（60–62）`70000 / 4 / 4 / 0`，浓缩钚（63–65）`70000 / 2 / 4 / 6`，激发铀（66–68）`6000 / 48 / 64 / 0`，激发钚（69–71）`10000 / 64 / 64 / 2`。
- 锂和荧石增殖棒（72–73）只在热阶段接收相邻棒的内部燃料脉冲。每脉冲增加 `1 + floor(堆温 / 3000)` 的进度，达到 10000 后停止反应。增殖棒不反射脉冲。 [增殖棒行为](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/api/items/ItemBreederCell.java)

## 反应堆与热传递

- 一个反应堆更新为 20 个 Minecraft 游戏刻。20 TPS 下等于 1 秒。反应堆有 6 行和 `3 + 扩容仓数` 列。每次按行优先遍历，先执行热阶段，再访问仍在槽中的燃料组件执行电阶段。电阶段回调产生的原始输出按照游戏顺序用 `float` 累加，再乘 IC2 的 `5f` 与配置 `5f` 得到 EU/t，总 EU 按每更新 20 游戏刻积分。
- 有效燃料组件的每个内部燃料单元发送 `1 + floor(内部单元数 / 2)` 个自脉冲，并按左、右、上、下访问四邻。电阶段给接收脉冲的燃料计算输出。普通反射板把电阶段脉冲弹回源燃料；热阶段每次反射损耗 1，最后一次有反射效果，在同一更新的后续回调中即从槽中消失。薄板和厚板的耐久是 10000 和 40000，铱板不损耗。
- 热阶段以 `n(n+1)/2` 为单元热值，乘对应燃料热系数并取整。流体模式下 MOX 类燃料在其每个内部单元发热前重读堆温，在严格大于堆容量一半时将该单元的热量翻倍。热量按左、右、上、下顺序逐个分摊给当时可接受热的元件，最后将正的剩余量进入堆壳。燃料耗尽判定在每个阶段结束，耐久只在热阶段增加。全新的钍、铀、MOX 棒在连续点火下分别在第 49999、19999、9999 次电阶段结束时耗尽。耗尽产物保留在同一槽，后续的组件回调无法再从它获得脉冲。 [燃料耗尽产物](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/common/items/ItemDepletedCell.java)
- IC2 散热片储热上限均为 1000 HU，普通、高级、堆芯、超频的自散热分别为 6、12、5、20 HU/更新。堆芯和超频分别每更新从堆壳获取至多 5、36 HU。元件散热片从各相邻可储热元件散热至多 4 HU。热交换器按当前热量百分比逐邻计算，保留游戏中的分段速率及低热量分支。普通、高级、堆芯、元件交换器的容量是 2500、10000、5000、5000 HU，对邻居的交换上限为 12、24、0、36 HU，对堆温的上限为 4、8、72、0 HU。
- 普通 IC2 冷却单元容量为 10k、30k、60k HU。GT 氦与 NaK 单元各有 60k、180k、360k HU，空间单元有 180k、360k、540k、1080k HU。1G 中子热容单元（59）为 1,000,000,000 HU。储热组件恰好等于容量时仍存在，超过容量时移除。原游戏对熔毁的 `alterHeat` 返回 `容量 − 尝试的新热量 + 1`，因此有符号的返回还会改变当次对剩余邻居的分摊。GT 单元被电模式加热后会设置标记，带标记的单元被放入流体模式时不参与正常储热，取用全冷备件时重置标记。 [GT 冷却单元](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/api/items/ItemCoolantCellIC.java)
- RSH/LZH 的可耗尽吸热预算是 20000/100000 HU。填满后不再参与分配，未满时拒绝逆向冷却。它们对热交换器报告当前热量为 0、最大值为预算加 1。
- 堆壳初始容量 10000 HU，每更新重置并在热阶段逐个叠加反应堆镀板。普通、热容量、强化镀板分别增加 1000、2000、500 HU。传统模式热容量镀板仍保留旧值 1700 HU。游戏在两个阶段及流体处理后检查温度，绝对热量至少 4000 HU 才进入环境效果判断。环境效果阈值依次是 40%、50%、70%、85%，堆壳达到 100% 时发生爆炸。爆炸强度上限 100，活跃燃料、反射板、镀板及 GT 冷却单元均影响强度。
- IC2 压力容器流体模式需要 9 列。散热器释放的热量按每 1 点热产生 2 mB 的热冷却液换算，单次受供液及空罐容量所限，多余的换算量整除 2 后加回堆壳。模拟器用每个更新可换入并送出的 mB 上限表示玩家外围设备合计能力。

## 验证范围

- `GtnhComponentTest` 覆盖混合燃料脉冲归属、游戏顺序的浮点累计、耗尽后的同更新邻接行为、反射板最后一次脉冲、MOX 在单组件内跨温度阈值、冷却单元熔毁后的余量、冷凝器饱和及 GT 冷却单元标记。另有端到端旧布局、流体模式和完整钍周期测试。
- 热阶段的数值适配和产品配方来自固定版本。当前校验使用源码推导测试与 Planner 执行。Minecraft 世界内的红石相位、实际物品运输、世界环境损害、服务器 TPS 波动和整套游戏实测仍未验证。Nuclear Control 的外部信号、容器变更、热冷却液的下游换热与蒸汽链交由使用者按现场设备验证。
- 新增组件的中英文名称采用同版 `en_US.lang` 与官方 Prism ZIP 中的 `.minecraft/config/txloader/load/GregTech[gregtech]/lang/zh_CN.lang`，IC2 元件取自包内 JAR 的 `ic2/lang/zh_CN.properties`。新增物品沿用 Planner 现有同类图标，以显示名称区分。其材料清单按成品计数，未展开制造配方。 [GregTech 英文物品名](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/resources/assets/gregtech/lang/en_US.lang) · [官方客户端 ZIP](https://downloads.gtnewhorizons.com/Multi_mc_downloads/GT_New_Horizons_2.9.0_Java_17-26.zip)
