# 新生魔艺 GTO 性能重构记录

目标：Minecraft 1.20.1 / Forge 47.4.21 / Java 21。

基线：baileyholl/Ars-Nouveau `1.20`，提交 `2c74064bc753600d9b6600f102f8163e2d6764dd`。
1.21.1 对照：同仓库 `main`，提交 `fd8c9520722bb96a3e75febaf770d7078db5ac43`（5.13.3）。
当前范围：客户端粒子生成、八叉树魔源索引、GTM tick 与异步 tick、DataSyncLib 增量同步。
2026-10-16：用户暂停 1.21.1 内容回移；对照提交只保留用于后续研究，本次不回移玩法、物品、法术或其他内容。

## 当前进度

- 已接入 GTM TaskHandler 的 tick 与异步字段同步调度，补上区块 ticking 和临时法师方块的原始启停条件；尚待游戏内验证。
- 已实现 Level capability 持有的八叉树魔源索引；独立 JDK 测试通过 12,000 次随机增删、移动和查询对照，并覆盖对象身份、重复坐标和整数边界。
- 已接入 DataSyncLib 的魔源、颜色和粒子路径增量同步；去掉中继传输和分流中继周期性的全量 NBT 更新。
- 已将魔源跟随、仪式天空、召唤和取药动画接入客户端生成路径；仍需审查其余服务器粒子发送点。
- 2026-10-17：spotlessApply、spotlessCheck、Java 编译、测试、完整构建及普通/内嵌依赖 JAR 的重混淆均已通过，已生成本地测试产物。
- 客户端/专用服务器验证和实际带宽对比尚未进行；本次产物用于后续游戏内测试。

## spark 热点优化（2026-10-17，未构建）

输入：`反馈/profile-2026-10-17_06.48.11.sparkprofile`；服务器线程采样约 157 秒，间隔 4 毫秒。
以下是同一调用分支的包含子调用时间，存在嵌套，不能相加：

| 采样路径 | 时间 | 占服务器线程采样 |
| --- | --- | --- |
| 法术炮台 `BasicSpellTurret.shootSpell` | 10,288 ms | 6.56% |
| 炮台扣源 `SourceUtil.takeSourceWithParticles` | 4,708 ms | 3.00% |
| 伐木 `EffectFell.onResolveBlock` | 3,848 ms | 2.45% |
| 伐木连通遍历 `SpellUtil.DFSBlockstates` | 236 ms | 0.15% |

- 扣源先查询已有八叉树中的魔源罐和已注册特殊供源；有足量供源时跳过 `canTakeSource` 的兼容注入，避免其再次逐方块扫描。索引内没有足量供源时仍走原兼容路径；魔源罐优先级、单一供源及正魔源筛选保持原逻辑。
- `SpellStats` 的增幅计数改为循环，存在性查询提前结束；伐木每次施法只查询一次精准采集和时运参数。
- 连通遍历用 `ArrayDeque` 替代链表，直接迭代邻居，减少队列节点和 Stream 分配；保留原遍历顺序和采集数量上限。
- 未跳过领地/破坏事件、掉落生成或相邻方块更新；采样中伐木的主要剩余成本在这些实际游戏逻辑中。
- 独立校验：`scripts/check_profile_hotpaths.py` 抽取实际修改方法，与提交 `4138e7da2` 对照；5,000 次供源选择/扣除、9,000 次增幅查询、300 次连通遍历对照均通过。使用 Python 3 与 JDK 21，运行 `python scripts/check_profile_hotpaths.py --java <JDK21>/bin/java.exe`。
- 本轮仅做独立方法校验、Java 语法解析和改动段格式化、`git diff --check`；未调用 Gradle、未构建模组。校验使用简化的世界/供源替身，Forge/Mixin 集成和实际性能收益仍需游戏内复测；旧采样不代表修改后的收益。
- 2026-10-17 推送前：使用 JDK 21 执行 `spotlessApply spotlessCheck --offline --console=plain`，格式整理与检查通过；随后再次通过独立方法对照校验。本轮未执行编译或模组构建。

## 本地构建验证（2026-10-17）

- 环境：Windows、Zulu JDK 21、Gradle Wrapper 8.14.5、Forge 47.4.21。
- 命令：使用 JDK 21 执行 `gradlew.bat spotlessApply spotlessCheck build jarJar --console=plain`。
- 结果：`BUILD SUCCESSFUL`；`checkSourceOctree` 通过 12,000 次随机操作及对象身份、重复坐标、整数边界检查。
- 构建修复：MixinExtras 改用 Maven Central 的 `io.github.llamalad7` 坐标和 0.4.1 版本，移除重复的旧 JitPack 注解处理器依赖。
- 测试产物：`build/libs/ars_nouveau-1.20.1-4.12.7-gto.1-all.jar`，已执行 `reobfJarJar`，内嵌 MixinExtras 0.4.1 与 GeckoLib 4.4.4。
- 已检查成品的模组声明、Mixin 配置、refmap、JarJar 元数据及新增实现类；GTM、DataSyncLib 和其他必需前置仍须在游戏运行环境中安装。
- 构建日志：`build/reports/local-build.log`。编译有旧 API 弃用等警告，未出现编译错误。
- DataSyncLib：魔源值、上限、颜色和粒子路径由 GTM 异步任务调用字段级变更检测；使用 `all=false` 发送增量，新区块加载使用独立管理器生成初始全量数据。

## 待完成

- [ ] 通过 GTM TaskHandler 运行 tick 和异步增量同步。
- [ ] 魔源搜索改为随 Level 生命周期保存的八叉树索引。
- [ ] 持续粒子在客户端产生，服务器只传必要的状态或一次性触发。
- [x] 格式检查、编译、测试和完整构建。
- [ ] 客户端/专用服务器验证及网络流量对比。
- [ ] 在 GregTech-Odyssey 创建并交付 fork。

## 外部状态

2026-10-16：当前 GitHub 登录账号尝试向 GregTech-Odyssey fork 时返回 HTTP 403（权限不足）。

2026-10-16：用户指定先将当前进度推送到 `JuZiool/Ars-Nouveau-gto` 的 `1.20` 分支。保留上游 `baileyholl/Ars-Nouveau` 作为性能重构基线。

## 约束

- 遵循 GTOCore-Main `0.6.0` 的 AGENTS.md 与 CODING_GUIDELINES.md。
- 保留既有 NBT 存盘格式；网络使用独立的紧凑编码和 DataSyncLib 字段增量同步。
- 世界状态只在服务器线程修改。异步 tick 仅读取发布的同步快照。
- 维度索引通过 GTM ILevel capability 持有，随 Level 释放。
- 未验证的实现不能标为完成。
