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
- spotlessApply 和 spotlessCheck 已通过。完整编译尚未通过，构建和客户端/专用服务器验证未完成，暂无可交付 jar。
- 本次为 `1.20` 分支的性能重构进度提交，不作为经过完整验证的发布版本。

## 待完成

- [ ] 通过 GTM TaskHandler 运行 tick 和异步增量同步。
- [ ] 魔源搜索改为随 Level 生命周期保存的八叉树索引。
- [ ] 持续粒子在客户端产生，服务器只传必要的状态或一次性触发。
- [ ] 格式检查、编译、测试、完整构建和客户端/专用服务器验证。
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
