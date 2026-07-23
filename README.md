# hekuo's guard

适用于 **Minecraft 1.21.1 / Fabric Loader 0.18.5+** 的纯服务端反作弊 Mod，面向生电服务器设计。客户端无需安装，采用 Apache License 2.0。

## 明确允许

- X-Ray、自动放置/挖掘、Baritone 自动寻路、Litematica 投影打印机、Tweakeroo。
- TNT/地毯/铁轨复制、更新抑制及其他原版生电机制。
- Fuji `/fly`：只要 Fuji 向玩家授予原版飞行能力，移动检测会自动豁免该玩家。

## 防护范围

- 非法移动坐标、移动包洪泛、明显超速与穿墙移动。
- 超出服务端实体交互距离的攻击、隔墙攻击、攻击包洪泛和异常多目标切换。
- 生存模式无合法飞行能力时的持续滞空、无实体方块支撑的水上行走，以及服务端可见的生存模式无敌状态。
- 不检测方块放置、挖掘、视角、矿物和路径行为。
- 玩家登录后的前 15 秒不进行移动计分，避免出生点、区块加载和客户端位置同步造成误判。
- 创造/旁观、Fuji 授权飞行、鞘翅、载具、液体、梯子、药水和强外力移动均不触发飞行检测；Fuji `/god` 会作为生存模式免伤处罚。

## 可选客户端检测

同一 JAR 可安装在客户端。服务器会向已安装客户端下发 `clientDetection.blockedModIds`，客户端发现命中项后上报，服务器立即永久封禁该账号。默认名单包含 Wurst、Meteor（`meteor-client`）、Aristois、BleachHack、LiquidBounce、Inertia、Impact、KAMI、Lambda、RusherHack、Coffee 和 Konas；可在 `config/hekuos_guard.json` 中调整。未安装本 Mod 的客户端不会上报，因此这是一层附加检测，不替代服务端反作弊。

如需强制玩家安装本 Mod，请将 `clientDetection.requireClientMod` 改为 `true` 并执行 `/hg reload`。该选项默认关闭；开启后，没有声明 `hekuo's guard` 客户端网络通道的客户端会在进入后立刻被拒绝。此机制只能确认客户端声明并支持本 Mod，不能阻止恶意修改后的客户端伪造该声明，服务端检测仍应保持开启。

## 安装

1. 安装 Fabric Loader、Fabric API，并将构建出的 JAR 放入服务器 `mods/`。
2. 第一次启动会生成 `config/hekuos_guard.json`。
3. 使用 `/hg alerts` 订阅管理员告警；使用 `/hg reload` 热重载配置。

默认情况下，累计 5 分开始告警，异常移动会立即回到最近安全点，累计 20 分自动封禁。

## 自动封禁

`/hg unban <玩家名>` 仅解除当前封禁并保留处罚等级；`/hg unban <玩家名> --force` 会同时清除处罚历史。

封禁记录保存在 `config/hekuos_guard_bans.json`，重启后仍然有效。首次自动封禁为 **1 小时**；分数在 60 秒窗口内增长越快，处罚等级会跳得越高。默认时长为 1 小时、2 小时、4 小时、8 小时、永久封禁。管理员可用 `/hg unban <玩家名>` 解封（不区分大小写，玩家无需在线），`/hg exempt <player> <seconds>` 可用于临时兼容特殊装置或活动。
