# hekuo's guard

面向 Minecraft 1.21.1 Fabric 生电服务器的保守型反作弊 Mod。默认仅服务端安装即可使用；同一个 JAR 也可以安装到客户端，以启用可选的客户端规则上报和安全握手。

| 项目 | 值 |
| --- | --- |
| 当前版本 | 1.0.4 |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.18.5 或更高版本 |
| Java | 21 |
| 许可证 | Apache License 2.0 |

## 设计原则

hekuo's guard 优先避免误判，只根据服务端可观察到的行为作出处理。它不尝试识别玩家装了什么客户端 Mod，也不会限制正常的技术玩法。

明确允许：

- X-Ray、自动放置、自动挖掘、Baritone 自动寻路、Litematica 投影/打印机、Tweakeroo；
- TNT、地狱门、铁轨等复制机、更新抑制与其他原版生电机制；
- Fuji `/fly`。只要 Fuji 或其他服务端插件授予原版飞行能力，移动检测会自动豁免；
- 创造、旁观、鞘翅、载具、液体、梯子、药水效果、活塞/史莱姆/爆炸等外力移动。

## 检测范围

- 非法/非有限坐标与旋转、异常移动/实体交互/载具包洪泛、持续超速、穿墙移动；
- Timer 类客户端的单 tick 累计位移超限；
- 生存模式下持续非法飞行、水上行走；
- 超距离攻击、隔墙攻击、攻击包洪泛和异常多目标切换；
- 生存模式下服务端可见的异常无敌状态（例如 Fuji `/god`）。

玩家刚进入服务器后的默认 15 秒内不计移动违规分，避免出生点同步或区块加载导致误判。创造模式和旁观模式不会触发飞行或无敌检测。

## 安装

1. 在服务端安装 Fabric Loader 0.18.5+、Fabric API 和 Java 21。
2. 将 Release 中的 `hekuos-guard-1.0.4.jar` 放入服务端 `mods/` 目录。
3. 启动一次服务器，Mod 会生成 `config/hekuos_guard.json`。
4. 按需编辑配置，再执行 `/hg reload` 热重载。

客户端不安装本 Mod 时，服务端基础反作弊仍然生效。只有启用客户端功能或安全握手时，客户端才需要安装同版本 JAR 和 Fabric API。

## 配置

配置文件：`config/hekuos_guard.json`。以下是客户端相关的默认段落：

```json
{
  "clientDetection": {
    "enabled": true,
    "requireClientMod": false,
    "blockedModIds": [
      "wurst",
      "meteor-client",
      "aristois",
      "bleachhack",
      "liquidbounce",
      "inertia",
      "impact",
      "kami",
      "lambda",
      "rusherhack",
      "coffee",
      "konas"
    ],
    "secureHandshake": {
      "enabled": false,
      "timeoutSeconds": 10,
      "requireKnownIntegrity": false,
      "allowedClientSha256": []
    }
  }
}
```

`blockedModIds` 使用 Fabric Mod ID，而不是显示名称。例如 Wurst 为 `wurst`，Meteor 为 `meteor-client`。当安装本 Mod 的客户端收到规则且检测到命中项时，它会报告给服务器，服务器会立即永久封禁该账号。未安装本 Mod 的客户端不能上报，因此服务端移动/战斗检测仍必须保持开启。

将 `requireClientMod` 设置为 `true` 后，不支持 hekuo's guard 客户端网络通道的玩家会被拒绝加入。默认关闭，适合仍允许原版客户端进入的服务器。

### 离线模式安全握手

离线模式没有 Mojang 会话认证，也不保证 Minecraft 连接本身加密。`secureHandshake.enabled` 是针对这种场景的额外保护：

1. 服务端使用持久化 Ed25519 身份签名随机挑战；
2. 客户端首次连接时保存该服务端公钥指纹，之后指纹改变会拒绝响应；
3. 双方用 X25519 协商会话密钥；
4. 客户端用 AES-GCM 加密发送本 Mod JAR 的 SHA-256 完整性报告；
5. 服务端验证挑战、签名和加密数据；超时或验证失败会断开连接。

服务端身份私钥保存于 `config/hekuos_guard_identity.json`；请备份它，且不要上传或泄露。客户端信任记录保存于 `config/hekuos_guard_known_servers.json`。如果服务端更换身份文件，客户端需要删除对应的信任记录后重新连接。

这是一层自定义 Payload 加密协议，不是标准 TLS，也不能从根本上阻止完全被修改的客户端伪造完整性报告。若需仅允许指定客户端构建，请开启 `requireKnownIntegrity`，并把日志输出的客户端 SHA-256 指纹加入 `allowedClientSha256`。

## 处罚与封禁

违规分每 15 秒衰减 1 分。默认累计 5 分开始向订阅管理员告警，累计 20 分自动封禁。异常移动会先回弹到最近安全位置。

首次自动封禁为 1 小时；分数在短窗口内增长过快会跳过处罚等级并延长处罚。默认等级为 1 小时、2 小时、4 小时、8 小时、永久封禁。不会自动封 IP。

封禁记录保存在 `config/hekuos_guard_bans.json`，重启后仍有效。普通解封保留处罚等级；使用 `--force` 才会删除处罚历史。

## 管理命令

所有命令要求 OP 等级 3；控制台同样可使用。`/hekuosguard` 与 `/hg` 等价。

| 命令 | 说明 |
| --- | --- |
| `/hg reload` | 完整验证后热重载配置，并把客户端规则同步给在线玩家。 |
| `/hg alerts` | 为自己开关管理员告警订阅。 |
| `/hg status <player>` | 查看在线玩家当前违规分。 |
| `/hg reset <player>` | 清空在线玩家的违规分。 |
| `/hg exempt <player> <seconds>` | 临时豁免在线玩家，最长时间由配置限制。 |
| `/hg unban <player>` | 按玩家名解封，保留处罚等级。 |
| `/hg unban <player> --force` | 按玩家名解封并删除处罚历史。 |

## 构建

使用 Java 21：

```powershell
gradle build --no-daemon
```

构建产物位于 `build/libs/`。推送到 `main` 会由 GitHub Actions 生成滚动预发布；推送 `v*` 标签会生成对应的正式 Release。
