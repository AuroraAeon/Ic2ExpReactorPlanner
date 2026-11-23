# GTNH 2.9.0 钍燃料循环与真空冷却

## 两个反应堆仓、11 根四联钍燃料棒

导入 [被动冷却布局](../examples/gtnh-2.9.0-thorium-11-passive.txt) 即可模拟。反应堆使用两个扩容仓，盘面是 6 行 × 5 列。其余设置使用 EU 模式、堆温 0 HU、元件初始热量 0 HU、连续供给红石。布局含 11 根四联燃料棒（钍）、13 个超频散热片、2 个高级散热片。

```text
TOTOT
O.O.O
TOTAT
OTO.O
TOTOT
A.OTO
```

`T` 表示四联燃料棒（钍），`O` 表示超频散热片，`A` 表示高级散热片，`.` 表示空位。

燃料棒之间没有正交相邻的位置。每根棒每次反应堆更新产生 24 HU，11 根合计 264 HU。游戏依次向左、右、上、下的可接受热量元件分配每个内部燃料单元的热量，并在每次整数除法之后分配余量。15 个散热片按位置承载的热量为 9 个各 20 HU、4 个各 16 HU、1 个 12 HU、1 个 8 HU。超频散热片每次更新可排 20 HU，高级散热片可排 12 HU。测试中所有元件完好，堆温峰值 0 HU。

- **燃料周期**为 49,999 次反应堆更新。每次更新间隔 20 个游戏刻，在 20 TPS 下为 13 小时 53 分 19 秒。燃料棒登记耐久度为 50,000。GT 燃料棒在热量阶段推进损耗，并在损耗达到 49,999 后的输出阶段换为枯竭燃料棒。
- **发电量**约为 1,320 EU/t，整周期约 1.320 G EU。复现游戏的逐次单精度脉冲求和后，数值为 1,320.0013427734375 EU/t，整周期 1,319,974,942.746582 EU。
- **镥产量**来自离线处理 11 根四联枯竭钍燃料棒。按 2.9.0 热力离心机配方，得到 **22 份镥粉、44 份钍粉、66 份铁粉**。配方每棒使用 500 个游戏刻与 48 EU/t。串行处理且无超频合计 275 秒、264,000 EU。单联钍棒的配方产出 2 份小撮镥粉，统计中记为 0.5 份镥粉。此处产量按热力离心机计算，普通离心机的配方和产物不同。

## 真空冷冻机循环

可导入 [真空冷却布局](../examples/gtnh-2.9.0-thorium-11-vacuum.txt) 验证同一套燃料棒。所有 15 个散热片换成 60k 氦冷却单元。打开模拟页的真空冷却选项，设置 **90% 更换阈值、每类型 15 个备用冷却单元、1 台真空冷冻机、2 次常规超频**。15 个备用单元加上堆内的 15 个，共需 30 个单元。布局代码保存反应堆本身，外部冷却选项需单独设置。

在上述配置的全周期模拟中，堆温峰值为 0 HU，完成 236 次更换和 236 次再冷却，无备用品短缺或元件损坏。换出的冷却单元合计携出 12,744,000 HU，剩余 455,736 HU 储存在停堆时仍就位的单元内。冷冻配方耗电 6,796,800 EU。典型的 60k 氦冷却单元再冷却配方为 60 游戏刻、120 EU/t，2 次常规超频后每单元为 15 游戏刻、1,920 EU/t。13,500 的整数倍更新上，所有 15 个位置可能同时请求更换。按 15 个备用品计算，即使单台冷冻机逐个处理，完成这一批也仅需 225 游戏刻。

模拟在每次反应堆更新后检查单元。已完成冷却的单元先回到同类型备件库存，然后按行从左到右检查槽位。达阈值且有同类型备用品时，换入冷单元，热单元排队进入冷冻机。备件耗尽时保留在堆中的热单元并记录首次缺货时点，烧毁的元件从循环库存移除。模型将更换视为两次更新之间的原子操作，游戏里的管道、过滤、红石时序、停电、冷冻机维护和输入输出堵塞需在实际搭建时验证。

每台模拟冷冻机串行处理单个单元，按工作时间计入配方 EU，模拟截止时会计入在制品已经消耗的 EU。真空冷冻机的游戏逻辑可在输入变化时触发配方检查，并在当前配方完成后立即检查下一条配方。模型假定输入与输出即时可用，采用常规超频、关闭批处理。机器可在低于 1 游戏刻的超频条件下进行额外并行，如果要按单单元模型使用这类供电，需要逐件供料。高阶冷冻机、维护惩罚、批处理与世界中的输送延迟未建模。实例使用 15 刻的配方，未触及亚刻并行。

## 版本证据

- [GTNH 2.9.0 官方 Prism 实例](https://downloads.gtnewhorizons.com/Multi_mc_downloads/GT_New_Horizons_2.9.0_Java_17-26.zip) 中的中央目录包含 `gregtech-5.09.54.265.jar`、`industrialcraft-2-2.2.828-experimental.jar` 和 `IC2NuclearControl-2.7.17.jar`。该实例不含 NuclearHorizons，因此 GregTech 的钍燃料与产物配方生效。
- 燃料注册、脉冲计算与热力离心配方分别来自固定提交的 [LoaderGTBlockFluid.java](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/loaders/preload/LoaderGTBlockFluid.java)、[ItemRadioactiveCellIC.java](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/api/items/ItemRadioactiveCellIC.java) 和 [FissionFuelLoader.java](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/loaders/load/FissionFuelLoader.java)。
- 单元再冷却参数来自 [VacuumFreezerRecipes.java](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/loaders/postload/recipes/VacuumFreezerRecipes.java)。MV 配方为 120 EU/t，基础时长为 IC2 的 10/30/60 刻、氦和钠钾的 60/180/360 刻、空间单元的 180/360/540/1080 刻、1G 中子热容的 1,000,000 刻。
- [OverclockCalculator.java](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/api/util/OverclockCalculator.java) 定义普通超频乘 4 功率、除 2 时长，时长取整并至少为 1 游戏刻。[MTEMultiBlockBase.java](https://github.com/GTNewHorizons/GT5-Unofficial/blob/bb00f3f1023e20d3cce84cf09abb442d22a3a5a5/src/main/java/gregtech/api/metatileentity/implementations/MTEMultiBlockBase.java) 定义配方检查与推进。
- 官方实例中的简体中文组件名称来自 `.minecraft/config/txloader/load/GregTech[gregtech]/lang/zh_CN.lang`，IC2 原生散热片、换热器与隔板名称另取自包内 IC2 JAR 的 `ic2/lang/zh_CN.properties`。

结果通过源代码推导和本地自动化测试交叉验证。以上两个布局尚未在实际 GTNH 世界运行。
