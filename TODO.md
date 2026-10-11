# 我给 SimpleX 的建议/想要的功能：

目前所有更改以 7.0.4 为基础版本，改完一个就在这个文档的对应条目下面新增详细说明，并且将更改存储为差异补丁文件（diff），在进行更改时，应尽量考虑可移植/可升级性，为以后适配官方更高版本做准备。

对于 AI：假如遇到无法实现、实现困难、官方已实现、破坏现有逻辑和功能、或者改动不确定的地方，请询问我

- [x] 在桌面端选择换行键组合（比如用回车键发送，控制加回车换行，或者反过来，又或者把控制改成上挡之类的，总之是两个下拉菜单，分别控制发送以及换行，但是不知道放哪，可以在设置页面里开一个新的分类）
  - **已实现（基于 7.0.4）**：设置页新增桌面端专属「消息发送」分类，内含「发送消息的快捷键」与「换行的快捷键」两个下拉菜单，均提供 `Enter`、`Shift + Enter`、`Ctrl + Enter`、`Alt + Enter` 四种组合。
  - 新增枚举 `ChatEnterKeyCombination` 与偏好项 `messageSendKey`（默认 `Enter`）/ `messageNewlineKey`（默认 `Shift + Enter`），位置：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/SimpleXAPI.kt`。使用 `mkSafeEnumPreference` 存储，遇到不认识的旧值会安全回退到默认值，便于日后升级。
  - 两个组合始终互不相同：选择对方已占用的组合时会自动交换；含 `Cmd`/`Win` 的组合一律不匹配；未分配的修饰键组合不发送消息，而单独按 `Enter` 仍会换行，保证换行始终可用。
  - 输入框改动：`apps/multiplatform/common/src/desktopMain/kotlin/chat/simplex/common/platform/PlatformTextField.desktop.kt`；设置界面改动：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/SettingsView.kt`。
  - 未加入 `AppSettings` 跨设备导入/导出结构（该设置与设备、输入方式相关），因此备份格式保持不变。
  - 测试：新增 `ChatEnterKeyCombinationTest`，`./gradlew :common:desktopTest` 全部通过。差异补丁见 `diff/`。
- [x] 一键更改群中所有人的接收地址（其实就是重建所有连接，基本上相当于重新进群）
  - **已实现（基于 7.0.4）**：群资料页成员列表下方新增「更改所有成员的接收地址」一行（仅普通群显示，频道/中继群不显示），点击后先弹确认框。
  - 确认后对每一个拥有活动连接的成员依次调用既有的 `apiSwitchGroupMember`，并把返回的连接状态通过 `updateGroupMemberConnectionStats` 写回模型（主、副聊天上下文都会更新），行为与成员详情页里单个成员的「更改接收地址」完全一致。
  - 没有活动连接的成员会被跳过：核心端对这类成员会返回 `CEGroupMemberNotActive`；没有任何成员连接时该行不显示。单个成员失败只弹一次 `error_changing_address` 提示，不会中断其余成员。
  - 改动位置：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupChatInfoView.kt`（新增回调与按钮行）、`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/ChatInfoView.kt`（`SwitchAllMembersAddressButton`、`showSwitchAllMembersAddressAlert`）。
  - 不修改核心（Haskell）与网络协议，纯粹复用已有的单连接切换命令，便于日后跟随官方版本升级。
  - 测试：`./gradlew :common:desktopTest` 通过（该改动为 UI + 既有 FFI 调用序列，无独立可测纯函数）。差异补丁见 `diff/`。
- [ ] 手动指定接收地址（更改接受地址的时候添加一个手动选择的按钮，然后列出当前所有服务器，全部变成复选框的形式，还要有全选和反选按钮）
  - **需核心改动，暂缓（基于 7.0.4）**：已核实无法只改客户端。simplexmq（`cabal.project` 中固定版本 `efaad8e7`）的 `switchConnectionAsync :: AgentClient -> ACorrId -> ConnId -> AE ConnectionStats` 第二个参数只是命令关联 ID，不是服务器地址；目标服务器由 `switchDuplexConnection` 内部调用 `getNextSMPServer c userId (当前连接用到的服务器)` 自动挑选，没有外部指定入口。chat 层的 `APISwitchContact`/`APISwitchGroupMember`（含官方 stable 最新版）也都不带服务器参数，说明官方尚未实现该功能。
  - 要让「复选框选择服务器」真正生效，必须同时修改 simplexmq 与 simplex-chat 两个 Haskell 包；而 simplexmq 是外部 pin 住的 `source-repository-package`，本机也没有 cabal/stack/ghcup（只有 `/usr/bin/ghc`），核心无法编译与测试，故本轮暂缓，先做其余条目。
  - 日后有核心构建环境时的实现路径：simplexmq 支持指定目标服务器（扩展 `switchConnectionAsync`/`switchDuplexConnection`）→ chat 层扩展或新增切换命令并在 `Commands.hs` 解析新 JSON 参数 → 客户端在「更改接收地址」流程中加入复选框服务器列表（含全选/反选）。
- [x] 批量添加服务器（在 设置→高级设置→网络和服务器→你的服务器→添加服务器→手动输入服务器 里面允许一次添加多台服务器，使用换行分隔，这一次添加的所有服务器都统一使用下方的设置）
  - **已实现（基于 7.0.4）**：「添加服务器 → 手动输入服务器」的地址输入框现在支持多行：每行一个地址，一次即可添加多台服务器；空行会被忽略，重复地址只添加一次，每行首尾空白会去掉。
  - 本次添加的所有服务器共用同一份设置（「用于新连接」开关、角色等），逐行解析出各自所属的服务器运营商后再分别写入服务器列表，因此同一批可以混入不同运营商的地址。
  - 校验按行独立进行：只要有一行非法就会显示警告图标，但不会阻止其余合法地址被添加；非法地址会在提示框里逐条列出。多行输入时「测试服务器」不可用（结果有歧义），二维码也只在恰好一个合法地址时显示。
  - 编辑已有服务器时行为不变（`allowMultipleAddresses` 默认 `false`），避免影响既有流程。
  - 改动位置：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/networkAndServers/NewServerView.kt`（新增 `serverAddresses`，重写 `addServer` 支持批量）、`.../networkAndServers/ProtocolServerView.kt`（`CustomServer` 新增 `allowMultipleAddresses` 参数）、`apps/multiplatform/common/src/commonMain/resources/MR/{base,zh-rCN}/strings.xml`（新增提示文案 `smp_servers_new_server_multiple_hint`）。
  - 不修改核心（Haskell）与网络协议，仅把多行地址在客户端展开成多条既有的 `UserServer` 记录，便于日后跟随官方版本升级。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/BatchServersTest.kt`（6 个用例，覆盖单地址、多地址、`\r\n`/`\r` 换行、空行与空白、去重顺序、空输入），`./gradlew :common:desktopTest` 全部通过。差异补丁见 `diff/`。
- [ ] 服务器分组和备注（对手动添加的服务器允许手动分组和添加备注，不然现在全是地址根本不可读）
- [ ] 查看和复制头像（点击群和其他用户头像的时候）
- [ ] 群成员连接率和百分比显示（在群成员列表标题处，就像主页上方标题一样）
- [ ] 收起群成员列表（不然在大群里拉到最下面要很长时间）
- [ ] 删除已保存的消息（长按一条消息点开信息的时候，允许在历史记录那里对任何历史记录进行仅客户端侧的删除）
- [ ] 成员头像旁边的网络状态图标（其实就是把用户 页面→服务器→网络状态 右边的那个会变色的点，也复制到私聊标题、主页用户名左侧以及群用户列表的用户名左边）
- [ ] 反撤回（拒绝任何来自服务端的删除信息请求，比如管理员删除，也忽略限时消息超时自动删除，要能允许对每一个私聊/群设置）
