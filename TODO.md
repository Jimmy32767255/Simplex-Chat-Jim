# 我给 SimpleX 的建议/想要的功能：

目前所有更改以 7.0.4 为基础版本，改完一个就在这个文档的对应条目下面新增详细说明，并且将更改存储为差异补丁文件（diff），在进行更改时，应尽量考虑可移植/可升级性，为以后适配官方更高版本做准备。

对于 AI：假如遇到无法实现、实现困难、官方已实现、破坏现有逻辑和功能、或者改动不确定的地方，请询问我

## feat & add:

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
- [x] 服务器分组和备注（对手动添加的服务器允许手动分组和添加备注，不然现在全是地址根本不可读）
  - **已实现（基于 7.0.4）**：「添加服务器 → 手动输入服务器」与「编辑已有服务器」两个界面都新增了「分组与备注」一栏：分组名可选，备注可选，填写后服务器列表按分组显示（无分组的排在最前，保持原有顺序，之后按分组首次出现的顺序依次显示分组标题），备注作为第二行小字显示在地址下方。
  - 分组/备注只保存在客户端偏好里（`AppPreferences.serverMetadata`，`Map<String, ServerMeta>`，以服务器地址字符串为键），**不会**写进 `UserServer`：因为 `UserServer` 会被序列化成核心命令 `/_servers` 并从句柄端读回，任何客户端专属字段都会在下一次往返时丢失。因此不动核心（Haskell）与网络协议，便于日后跟随官方版本升级。
  - 编辑服务器地址时分组/备注会跟着迁移到新地址（旧键会被删除）；分组与备注都清空时对应记录会被删除，不留空条目。
  - 批量添加服务器时，本次成功添加的每个地址都会写入同一份分组/备注（非法地址会被跳过）。
  - 改动位置：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/SimpleXAPI.kt`（新增 `ServerMeta`、`SHARED_PREFS_SERVER_METADATA`、`serverMetadata`）、`.../views/usersettings/networkAndServers/ProtocolServersView.kt`（`ServersSection`、`serverGroup`、`updatedServerMetadata`、`saveServerMetadata`）、`.../networkAndServers/ProtocolServerView.kt`（`ServerGroupAndNoteSection`）、`.../networkAndServers/NewServerView.kt`、`apps/multiplatform/common/src/commonMain/resources/MR/{base,zh-rCN}/strings.xml`。
  - 与条目 1 一致，未加入 `AppSettings` 跨设备导入/导出结构，备份格式保持不变（代价是分组/备注不会随设备迁移一起带走，日后如需可再补上，新增可空字段是向后兼容的）。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/ServerMetadataTest.kt`（8 个用例，覆盖新增/更新/清空删除/改地址迁移/保留其他条目/分组名归一化），`./gradlew :common:desktopTest` 全部通过。差异补丁见 `diff/`。
- [x] 查看和复制头像（点击群和其他用户头像的时候）
  - 已实现（基于 7.0.4）：点击头像打开全屏查看弹窗（`ProfileImageViewer`，`ModalManager.fullscreen`），窗口内提供「复制图片」按钮；没有自定义头像（只有默认图标）时点击无任何反应。
  - 可点击位置：联系人详情页头部头像、群详情页头部头像、群详情页成员列表中的头像、群成员详情页头部头像。聊天列表、选人列表、欢迎页等处的头像保持原样（那些行本身已有自己的点击行为）。
  - 新增平台剪贴板接口 `expect fun copyImageToClipboard(image: ImageBitmap): Boolean`（`common/.../platform/Images.kt`）：桌面端用 AWT `Transferable`（只支持 `DataFlavor.imageFlavor`），Android 端把 PNG 写到应用文件目录的 `clipboard_image.png` 并通过 `FileProvider` 内容 URI 写入系统剪贴板（每次覆盖同一文件，剪贴板始终指向存在的文件）。
  - 新增字符串 `copy_image`、`image_copied`、`image_copy_failed`（base + zh-rCN）。
  - 与条目 1、5 一致，未改动 `AppSettings` 跨设备导入/导出结构，也未改核心（纯客户端功能）。
  - 测试：新增 `apps/multiplatform/common/src/desktopTest/kotlin/chat/simplex/app/ImageClipboardTest.kt`（4 个用例，覆盖 imageFlavor 支持、其他 flavor 不支持、取回同一张图、不支持 flavor 抛 `UnsupportedFlavorException`；因单元测试 JVM 不加载 Skia 原生库，测试直接覆盖 `bufferedImageToTransferable` 而非 `ImageBitmap` 转换），`./gradlew :common:desktopTest` 全部通过。差异补丁见 `diff/`。
- [x] 群成员连接率和百分比显示（在群成员列表标题处，就像主页上方标题一样）
  - 已实现（基于 7.0.4）：群详情页成员列表的分组标题右侧新增连接率指示：`已连接/总数 (百分比%)` 文字 + 一个会变色的圆点（全部连接为绿色 `ic_circle_filled`，部分连接为黄色 `ic_pending_filled`，无人连接为灰色 `ic_error_filled`），样式参照主页标题的订阅状态指示。
  - 「已连接」只统计 `GroupMember.ready`（即 `activeConn?.connStatus == ConnStatus.Ready`）的成员；仅 prepared/SndReady/失败的连接不算已连接。新增 `groupMembersConnection(members, currentMembership)` 计算分子分母，自己的成员身份同时计入分子与分母，因此与同一标题里的「N 位成员」保持一致，全部连接时显示 `N/N (100%)`。
  - 百分比用 `floor` 取整（与主页订阅指示一致），因此只有真正全部连接时才会出现 100%。
  - 只在非中继（`!groupInfo.useRelays`）且未处于 `nextConnectPrepared` 的群成员区显示，与原有成员区显示条件一致；成员搜索框不会影响该比率（用全量成员计算）。
  - 改动位置：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupChatInfoView.kt`（新增 `GroupMembersConnectionRate`、`groupMembersConnection`、`groupMembersConnectionRate`、`GroupMembersConnectionStatus`，标题改用 `SectionViewWithButton`）、`.../views/helpers/Section.kt`（`SectionViewWithButton` 新增可选的 `cardShape` 参数并把标题行改为垂直居中，默认值保证其他调用点行为不变）。
  - 与条目 1、5、6 一致，未改动 `AppSettings` 跨设备导入/导出结构，也未改核心（纯客户端显示）。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/GroupMembersConnectionRateTest.kt`（8 个用例，覆盖全部连接/部分连接/无人连接、SndReady 不计入、空群只算自己、总数与上界），`./gradlew :common:desktopTest` 全部通过。差异补丁见 `diff/`。
- [x] 收起群成员列表（不然在大群里拉到最下面要很长时间）
  - 已实现（基于 7.0.4）：群详情页成员列表标题行右侧新增折叠箭头（展开时 `ic_chevron_up`，收起时 `ic_chevron_down`），点击即可收起/展开成员列表。
  - 收起后会隐藏该区块里的「添加成员」按钮、成员搜索框、自己的那一行以及下方所有成员行，只保留「N 位成员 + 连接率」这行标题，因此在成员很多的群里可以快速滑到页面下方的群设置、清空聊天、删除群组、退出群组等操作。
  - 折叠状态按群保存、仅在弹窗打开期间有效（`rememberSaveable(groupInfo.id)`）：浏览某位成员详情再返回不会丢失，关闭弹窗后重置；不写入偏好设置，也未加入 `AppSettings` 跨设备导入/导出结构。收起不会清空成员搜索词或已勾选的成员，展开后恢复原状。
  - 可见性判断抽成语义化的纯函数 `groupMembersSectionVisible(nextConnectPrepared, useRelays)` 与 `groupMembersRowsVisible(nextConnectPrepared, useRelays, collapsed)`，成员区原有的显示条件（非中继群、且不在 `nextConnectPrepared` 状态）保持不变。
  - 改动位置：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupChatInfoView.kt`、`apps/multiplatform/common/src/commonMain/resources/MR/{base,zh-rCN}/strings.xml`（新增 `collapse_verb`，复用已有的 `expand_verb`）。
  - 测试：`apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/GroupMembersConnectionRateTest.kt` 扩充至 10 个用例（新增成员区与成员行可见性判定），`./gradlew :common:desktopTest` 全部通过。差异补丁见 `diff/`。
- [ ] 删除已保存的消息（长按一条消息点开信息的时候，允许在历史记录那里对任何历史记录进行仅客户端侧的删除）
  - **暂缓（基于 7.0.4，经确认后跳过）**：已核实这条需求有两种理解，且成本差别很大，与用户确认后决定先跳过、继续其余条目。
  - 理解一（删除「历史记录」标签页里列出的单个编辑版本）：核心（Haskell）的 `chat_item_versions` 表只有整条消息级的删除（`deleteChatItemVersions_`/`deleteMemberCIs` 按 `chat_item_id` 全删），`APIDeleteChatItem` 也只接受 `chatItemIds`，没有任何按单个版本删除的命令。要实现必须在核心新增命令，本机没有 cabal/stack/ghcup，核心无法编译与测试，因此和条目 3 一样只能暂缓。
  - 理解二（在信息弹窗里删除整条消息、仅本机）：客户端已经具备该能力（长按菜单的「DeleteItemAction」→「仅为我删除」走 `CIDeleteMode.cidmInternal`，频道/群管理员走 `cidmHistory`），信息弹窗内没有必须补的缺口，单独加一个按钮收益有限。
  - 日后如有核心构建环境且确认要做理解一，实现路径：核心新增「按 `chat_item_version_id` 删除单条版本」的命令（`Controller.hs` 的 API 命令 + `Store/Messages.hs` 删除函数 + `Library/Commands.hs` 处理）→ 客户端在 `ChatItemInfoView.ItemVersionView` 的下拉菜单里加「仅本机删除该版本」（必要时刷新 `apiGetChatItemInfo`）。
- [x] 成员头像旁边的网络状态图标（其实就是把用户 页面→服务器→网络状态 右边的那个会变色的点，也复制到私聊标题、主页用户名左侧以及群用户列表的用户名左边）
  - 已实现（基于 7.0.4）：把联系人详情页「服务器」区里「网络状态」那一行的圆点抽成公共组件 `SubscriptionStatusDot`（`views/helpers/SubscriptionStatusDot.kt`），并在三处复用：私聊标题、主页（聊天列表）每行的联系人名字左侧、群成员列表每个成员的名字左侧。图标与颜色和信息页完全一致（`Active` 绿色 `ic_circle_filled`、`Pending` `ic_pending_filled`、`Removed`/`NoSub` `ic_error_filled`，后两者用主题 secondary 色）。
  - 状态来源：7.0.4 客户端原本只跟踪「当前打开的聊天」的订阅状态（`chatSubStatus`/`chatAgentConnId`），核心在 `CEvtSubscriptionStatus` 里带出的其它连接状态（`connections` 是 agent 连接 id 列表）被丢弃；现在 `ChatModel.connectionSubStatuses`（agent 连接 id → `SubscriptionStatus`）记录每条连接的状态，取值优先级为「实时事件 → 该连接最近一次拉取的 `ConnectionStats.subStatus`」，由纯函数 `connectionSubStatus(conn, liveStatuses)` 封装。这样聊天列表和成员列表里的连接也有状态可显示（`connectionStats` 只在打开信息页/同步等操作时才拉取）。
  - 私聊标题的兜底：只有当连接的 `agentConnId` 等于当前打开聊天的 `chatAgentConnId` 时才回退到 `chatSubStatus`（`chatSubStatus` 在关闭聊天时不会重置，否则会显示过期状态）。原来的 `SubStatusView`/`SubProgressView`（Active 不显示、Pending 显示转圈、失败显示错误图标）已删除，改由统一的圆点显示，因此连接正常时标题也会显示绿点。
  - 「主页用户名左侧」按主页聊天列表里每行的联系人名字处理（主页顶部只有头像和「聊天」标题，没有用户名）；群/频道行不显示圆点（没有单一连接），群成员的圆点在成员列表里。
  - 没有状态时圆点不渲染任何内容（`SubscriptionStatusDot(null)` 直接返回），不占位、不影响行高；成员行额外要求 `activeConn != null`，联系人要求 `ready && active`（与信息页一致）。
  - 与条目 1、5、6、8 一致：未改动 `AppSettings` 跨设备导入/导出结构，未改核心（纯客户端显示），未新增字符串（复用已有的 `icon_descr_server_status_connected`/`icon_descr_server_status_disconnected`/`icon_descr_server_status_error`）。
  - 改动位置：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/helpers/SubscriptionStatusDot.kt`（新增）、`.../model/ChatModel.kt`（`connectionSubStatuses`、`connectionSubStatus`）、`.../model/SimpleXAPI.kt`（`CR.SubscriptionStatusEvt` 处理）、`.../views/chat/ChatView.kt`、`.../views/chatlist/ChatPreviewView.kt`、`.../views/chat/group/GroupChatInfoView.kt`、`.../views/chat/ChatInfoView.kt`（`SubStatusRow` 改用公共组件，删除私有 `ServerImage`）。
  - 文档：`product/concepts.md`（PC37）、`product/rules.md`（RULE-22）、`product/README.md`、`product/views/{chat-list,chat,contact-info,group-info}.md`、`spec/impact.md`（新增 PC37 行与 `SubscriptionStatusDot.kt` 行，并补齐缺失的 PC33–PC36 图例）、`spec/state.md`、`spec/client/{chat-list,chat-view}.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/ConnectionSubStatusTest.kt`（7 个用例，覆盖无连接、无状态、实时事件优先、回退到 `connectionStats`、忽略其它连接的状态、`Removed`、`NoSub`），`./gradlew :common:desktopTest` 全部通过。差异补丁见 `diff/`。
- [ ] 反撤回（拒绝任何来自服务端的删除信息请求，比如管理员删除，也忽略限时消息超时自动删除，要能允许对每一个私聊/群设置）
  - **暂缓（基于 7.0.4，经确认后跳过）**：已核实 7.0.4 核心（Haskell）的三种删除机制，纯客户端无法做到「拒绝删除 + 保留内容 + 忽略限时超时」，与用户确认后决定先跳过、其余条目照旧。
  - 私聊/群的「对所有人删除」：核心收到对端的 `XMsgDel` 后走 `Subscriber.hs:2053 messageDelete`（私聊）与 `Subscriber.hs:2333 groupMessageDelete`（群）。删还是留由**接收端自己**已有的功能开关决定：`featureAllowed SCFFullDelete`/`SGFFullDelete` 为真时执行 `Internal.hs:467 deleteDirectCIs`/`:479 deleteGroupCIs`（`DELETE FROM chat_items` 并顺带删 `chat_item_messages`、`chat_item_versions`，内容不可恢复）；为假时执行 `Internal.hs:562 markDirectCIsDeleted`/`:574 markGroupCIsDeleted`，即 `Store/Messages.hs:2645 markDirectChatItemDeleted` 只更新 `item_deleted`/`item_deleted_ts`/`updated_at`，**内容列仍然保留**。私聊还有 78 小时的窗口限制（`Subscriber.hs:2073 rcvItemDeletable`）。
  - 群管理员删除本来就是「标记」而非真删（`Subscriber.hs:2220 markGroupCIsDeleted … (Just moderator)`），内容同样保留。
  - 客户端目前不管内容是否还在，都只渲染通用占位文本：`MarkedDeletedItemView.kt` 的 `markedDeletedText` 只返回 `marked_deleted_description`、`moderated_item_description` 等固定文案，从不显示仍留在 `ci.content` 里的原文。
  - 限时消息超时删除是**真删**：`Internal.hs:1647 deleteTimedItem` 在到期后调用 `deleteDirectCIs`/`deleteGroupCIs`，内容被销毁，客户端无法恢复。能否安排删除定时器在收到消息那一刻就已决定：只有该聊天开着「限时消息」偏好时，`Messages.hs:586 contactTimedTTL`/`:594 groupTimedTTL` 才会让收到的消息带上 `timed_delete_at`（`Messages.hs:605 rcvCITimed_`）。
  - 因此忠实实现需要在核心新增一个**独立的**每私聊/群偏好（当前两种删除都复用互相协商的 `SCFFullDelete`/`SGFFullDelete` 与 `SCFTimedMessages`/`SGFTimedMessages`，把它们当反撤回开关会同时改变对端看到的能力），并让 `messageDelete`/`groupMessageDelete`/`deleteTimedItem` 在该偏好下跳过删除或改为标记。本机没有 cabal/stack/ghcup，核心无法编译与测试，只能提交未经编译验证的补丁，日后适配官方新版也更麻烦，所以按 TODO 头部的约定询问后跳过。
  - 纯客户端的近似做法及其代价（未采用）：借用既有开关——关掉该聊天的「完整删除」使对端撤回只标记、关掉「限时消息」使收到的消息不再安排删除定时器，再让客户端显示标记删除项保留的原文。代价是本端也失去「对所有人删除」的能力，且对端会看到本端不支持这两个功能，与需求语义不完全一致。
  - 日后如有核心构建环境且确认要做，实现路径：`Types/Preferences.hs` 新增 `SCFKeepDeleted`/`SGFKeepDeleted`（补进 `allChatFeatures`/`allGroupFeatures`、`chatPrefSel`/`groupPrefSel`、`allChatPrefs`/`allGroupPrefs` 的映射与序列化）→ `Store/SQLite/Migrations` 加迁移、`Store/Shared.hs` 读写偏好列 → `Subscriber.hs` 的 `messageDelete`/`groupMessageDelete`/管理员删除分支与 `Internal.hs:1647 deleteTimedItem` 在该偏好下跳过删除或改为标记 → 复用既有 `APIUpdateChatItem`/群偏好命令下发 → 客户端在 `ContactPreferences.kt`/`GroupPreferences.kt` 加开关，并让 `MarkedDeletedItemView` 显示保留的原文。
- [x] 导入和导出自定义服务器（导出为 JSON 文件；导出时提示小心服务器验证密钥和地址泄漏；导入时提示仅应导入受信任的服务器）
  - 入口：在「你的服务器」页面（`NetworkAndServersLayout`，SOCKS 代理脚注与「保存服务器」之间）新增「导入和导出服务器」区块 `ServersImportExportSection`，导出、导入各一行；导出前弹确认框提示「文件包含你自定义服务器的地址和验证密钥，请妥善保管」（地址串里带 key hash 与 basic-auth 密码），导入前弹确认框提示「请仅导入你信任的服务器」（导入的服务器可以看到连接元数据）。
  - 导出：只导出非预设（`preset = false`）、未删除、地址非空的服务器，SMP 与 XFTP 分开；每条保留 `enabled`/`tested`/角色覆盖（`ServerRolesOverride`）/所属 `OperatorTag`，以及客户端侧 `serverMetadata` 里的分组与备注。文件为 `kotlinx.serialization` 生成的 JSON（`version = 1`，prettyPrint、写出默认值、忽略未知字段），保存对话框默认文件名 `simplex-servers.json`。
  - 导入：文件解析失败弹「导入服务器出错」；成功则按协议分别校验每个地址（用核心的 `parseServerAddress`），与所在列表协议不一致、无法解析或无效的计入 `invalid`，已存在于同协议的计入 `duplicates`，**从不替换现有列表**。有效的服务器追加到 `OperatorTag` 相同的分组；文件里没带标签或找不到该分组时落到「无 operator」分组，必要时新建。确认框会先列出「将添加 N 个 / 已存在跳过 N 个 / 无效跳过 N 个」，确认后写回 `serverMetadata` 并复用既有 `saveServers`（`setUserServers` + 刷新 `currUserServers`/`userServers`），与手动添加服务器走同一条保存与校验路径。
  - 结构与可测性：纯逻辑单独放在 `ServersBackup.kt`；因为 `chatParseServer` 是 JNI 调用、桌面单测无法加载，地址解析器以参数注入（默认 `ServerAddress.parseServerAddress`），导出过滤、JSON 编解码、合并去重与元数据合并都可单测。UI 必须放在 `NetworkAndServers.kt` 内，因为 `saveServers` 是文件私有。
  - 不改动 `AppSettings`/核心：没有新增或改动 `/_servers`（`UserServer` 仍会被核心原样回读），备份 JSON 只是客户端本地文件；与条目 1、4、5、6、8、10 的约定一致。导入的服务器一律 `preset = false`、`deleted = false`、无 `remoteHostId`/`serverId`。
  - 改动位置：新增 `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/networkAndServers/ServersBackup.kt`（`ServersBackup`、`ServersBackupItem`、`serversBackupOf`、`exportServersBackup`、`parseServersBackup`、`mergeServersBackup`、`importedServerMetadata`）；`.../networkAndServers/NetworkAndServers.kt`（`ServersImportExportSection`、`serversImportSummary` 及调用点）；`common/src/commonMain/resources/MR/base/strings.xml` 与 `MR/zh-rCN/strings.xml`（各 13 条新字符串）。
  - 文档：`product/concepts.md`（PC38）、`product/rules.md`（RULE-23）、`product/README.md`、`product/views/settings.md`（新增「Import and Export」小节）、`spec/impact.md`、`spec/api.md`（§2.11 服务器备份 JSON 格式）、`spec/state.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/ServersBackupTest.kt`（17 个用例：导出过滤预设/已删除/空地址、保留设置与分组备注、XFTP 导出、JSON 往返与非法输入、合并到同名 operator 分组、无标签回退、新建无 operator 分组、去重、已删除地址视为不存在、无效与协议不匹配、保留导入设置、同组追加 SMP+XFTP、元数据写回与清理陈旧项），`./gradlew :common:desktopTest` 全部通过（17/17）。差异补丁见 `diff/`。
- [x] 在主页聊天列表的群名称左侧显示群成员连接率圆点（把「群成员连接率和百分比显示」里的那个会变色点也放到主页群名左边，样式同条目 10 的网络状态图标；主页列表本身没有成员数据，需要后台预取每个群的成员列表）
  - 已实现（基于 7.0.4）：主页聊天列表里，普通群聊的名称左侧新增成员连接率圆点——全部成员已连接为绿色 `ic_circle_filled`，部分连接为黄色 `ic_pending_filled`，无人连接为灰色 `ic_error_filled`。它与群详情页成员列表标题用的是同一个组件 `GroupMembersConnectionRateDot` 和同一套计算 `groupMembersConnection`/`groupMembersConnectionRate`，两处不会出现不一致。
  - 主页列表本身没有成员数据，因此在 `ChatListView` 里按会话后台预取：每个普通群只调用一次 `apiListMembers`（已请求的群记录在 `ChatModel.groupRateRequested`），跳过频道/中继群（`useRelays`）和自己已不在群里的群，结果写入会话级缓存 `ChatModel.groupMembersForRate`。预取在后台线程串行进行，不显示任何进度；请求失败（例如群恰好被删除）会被捕获并静默忽略，只是不显示该群的圆点。
  - 成员变化由 `CR.GroupMemberUpdated` 驱动 `ChatModel.updateGroupMembersForRate` 更新缓存：活跃成员替换或追加、失效成员移除、未缓存的群忽略，因此连接率会随后台事件刷新，无需重新打开聊天。
  - 圆点只在成员列表已知时显示（不会为了"占位"而显示未知状态），中继群和 `nextConnectPrepared` 状态的群不显示（与群详情页 `groupMembersSectionVisible` 的判断一致），无圆点时不留空隙、不影响行高。
  - 改动位置：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chatlist/ChatPreviewView.kt`（群名左侧圆点）、`.../views/chatlist/ChatListView.kt`（后台预取）、`.../views/chat/group/GroupChatInfoView.kt`（抽出公共组件 `GroupMembersConnectionRateDot`）、`.../model/ChatModel.kt`（`groupMembersForRate`、`groupRateRequested`、`updateGroupMembersForRate`、`groupChatsForConnectionRate`）、`.../model/SimpleXAPI.kt`（`CR.GroupMemberUpdated` 刷新缓存）。
  - 与条目 1、5、6、8、10 一致：不修改核心（Haskell）与网络协议，不修改 `AppSettings` 跨设备导入/导出结构，未新增字符串（复用已有的 `ic_circle_filled`/`ic_pending_filled`/`ic_error_filled`），缓存只在本次会话内存中存在、不写入磁盘。
  - 文档：`product/concepts.md`（PC39）、`product/rules.md`（RULE-24）、`product/README.md`、`product/views/chat-list.md`、`product/views/group-info.md`、`spec/impact.md`、`spec/state.md`、`spec/client/chat-list.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/GroupConnectionRateCacheTest.kt`（10 个用例：普通群/频道/未激活成员/已请求/非群聊的预取筛选，以及成员更新时的替换、追加活跃成员、移除失效成员、忽略未缓存群、不追加失效的新成员），`./gradlew :common:desktopTest` 全部通过（10/10，全部 17 个测试类无失败）。差异补丁见 `diff/`。

- [x] 在设置里添加一个开关允许将收藏视为置顶（开启后已收藏的联系人或群会永远显示在主页对话列表的最上方）
  - 已实现（基于 7.0.4）：新增客户端布尔偏好 `AppPreferences.favoritesAsPinned`（默认关闭，键名 `FavoritesAsPinned`），设置页新增「对话列表」（`settings_chat_list`）分区，其中的开关「将收藏视为置顶」（`favorites_as_pinned`）带一行说明文字。开启后主页对话列表把已收藏的联系人和群整体移到最上方。
  - 排序实现是纯显示层的：`ChatModel.kt` 新增 `isFavoriteChat(chat)`（即 `chat.chatInfo.chatSettings?.favorite == true`，与收藏筛选和星标图标用同一个字段）和 `favoritesPinnedFirst(chats, favoritesAsPinned)`（用 `partition` 稳定分组，收藏在前、其余在后，两组内部原有顺序不变）；`ChatListView` 在 `filteredChats(...)` 之后调用它，偏好关闭时原样返回。`ChatModel.chats` 的顺序、内核数据和数据库都没有被改动，关掉开关立即恢复原顺序。
  - 只影响主页对话列表：复用 `filteredChats` 的其他界面（例如分享列表）不会被重排；搜索/筛选结果同样适用这条规则（收藏永远在上方），这一点也已写进文档。
  - 不修改核心（Haskell）与网络协议，不修改 `AppSettings` 跨设备导入/导出结构（新的偏好只是本机设置），未改任何既有字符串。
  - 改动文件：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/SimpleXAPI.kt`（`favoritesAsPinned` + `SHARED_PREFS_FAVORITES_AS_PINNED`）、`.../model/ChatModel.kt`（`isFavoriteChat`、`favoritesPinnedFirst`）、`.../views/chatlist/ChatListView.kt`（应用排序）、`.../views/usersettings/SettingsView.kt`（「对话列表」分区与开关）、`common/src/commonMain/resources/MR/base/strings.xml` 与 `MR/zh-rCN/strings.xml`（新增 3 条字符串）。
  - 文档：`product/rules.md`（RULE-25）、`product/concepts.md`（PC40）、`product/README.md`、`product/views/chat-list.md`（Chat List Order）、`product/views/settings.md`（Chat List Section）、`spec/client/chat-list.md`（Chat List Order）、`spec/state.md`（`favoritesAsPinned`）、`spec/impact.md`、`plans/2026-10-22-favorites-pinned-first.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/FavoritesPinnedFirstTest.kt`（8 个用例：关闭时顺序不变、收藏移到最上方、两组内部顺序保持、没有收藏/全是收藏时不变、空列表、无聊天设置的对话不算收藏、联系人与群都被识别），`./gradlew :common:desktopTest` 全部通过（8/8，全部 19 个测试类共 115 个用例无失败），`:common:compileDebugKotlinAndroid` 也通过。差异补丁见 `diff/`。

- [x] 添加选中消息后批量复制到剪贴板的功能（包含发送者名称（如果有备注先用备注）、发送时间（不是收到时间）和正文内容（最新版本））
  - 已实现（基于 7.0.4）：多选工具栏（删除/举报/转发那一行）最右侧新增「复制选中消息」按钮，一键把选中的消息复制为一整段文本，说明文字为内容描述（`copy_selected_messages`），使用现有的 `ic_content_copy` 图标，无可复制内容时置灰。每条消息的格式为 `发送者, 发送时间` 换行 `正文`，消息之间用一个空行分隔，顺序与聊天中的对话顺序一致，最旧的消息在最前面（`ChatModel.chatItems` 本身就是最旧在前，界面只是用 `reverseLayout` 反向渲染；复制时由 `selectedChatItemsInOrder` 原样保留这个顺序，不做反转）。
  - 三处要求的取值：① 发送者名称为收件消息的发送者——群聊取群成员、私聊取联系人，并按 `localAlias.ifEmpty { displayName }` 优先使用「备注」（`ciSenderName`）；自己发送的消息与本地笔记没有发送者名称，只复制时间，与聊天里的显示一致。② 时间用 `meta.itemTs`（发送时间）经现有的 `getTimestampText` 格式化，绝不使用 `meta.createdAt`（收到时间），也不需要新增时间格式字符串。③ 正文用 `ChatItem.content.text`，即内核在每次编辑后替换成的最新版本；历史版本只存在 `ChatItemInfo.itemVersions` 里，需要额外请求，因此批量复制不拉取它、不产生网络往返。
  - 边界：只复制可复制的项（`canCopyChatItem` 排除墓碑/实时的语音占位（`isLiveDummy`）与举报内容），已删除项会被跳过，不会产生空段落；文件与语音按 `ChatItem.text` 复制成文件名或「语音消息（时长）」的描述，而不是像单选复制那样在桌面端先下载再复制路径（批量下载不可行）；复制上限 200 条（与删除一致），避免超大选择卡住界面或撑爆剪贴板。
  - 改动文件：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/ChatModel.kt`（新增纯函数 `ciSenderName`、`canCopyChatItem`、`chatItemToText`、`chatItemsToText`、`selectedChatItemsInOrder`，时间格式化由调用方注入以便测试）、`.../views/chat/SelectableChatItemToolbars.kt`（复制按钮 + `recheckItems` 里的可用性计算）、`.../views/chat/ChatView.kt`（两处工具栏调用点的 `copyItems` 回调；举报内容那一处传空实现）、`common/src/commonMain/resources/MR/base/strings.xml` 与 `MR/zh-rCN/strings.xml`（新增 1 条字符串）。
  - 与条目 1、5、6、8、10、13、14 一致：不修改核心（Haskell）与网络协议，不修改 `AppSettings` 跨设备导入/导出结构，不新增偏好或持久化状态，纯客户端拼接文本。
  - 文档：`product/rules.md`（RULE-26）、`product/concepts.md`（PC41）、`product/README.md`、`product/views/chat.md`（多选工具栏与右键菜单表格）、`spec/client/chat-view.md`（§3 消息列表与源文件表）、`spec/impact.md`、`plans/2026-10-28-copy-selected-messages.md`。
  - 修复记录：初次提交（`19a6203e2`）里复制顺序反了——误以为 `ChatsContext.chatItems` 是最新在前，于是多做了一次 `asReversed()`，导致最新的消息跑到文本开头。实际上该列表是最旧在前（界面靠 `reverseLayout` + `asReversed()` 渲染），已去掉多余的反转，并抽出 `selectedChatItemsInOrder` 把「原样保留列表顺序」这件事固定下来（附两个用例）。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/SelectedMessagesCopyTest.kt`（14 个用例：群消息用备注、群消息无备注用昵称、私聊用备注/无备注、自己的消息无名称、用的是发送时间而不是收到时间、多条消息按对话顺序并用空行分隔、已删除项被跳过、文件消息复制文件名、空选择得空文本、`canCopyChatItem` 判断、本地笔记无发送者名称、选择顺序保留最旧在前、选择忽略未选中项并保持顺序），`./gradlew :common:desktopTest` 全部通过（14/14，全部 20 个测试类共 129 个用例无失败）。差异补丁见 `diff/`。

- [x] 复制选中消息时，自己发送的消息前面也带上自己的名字
  - 已实现（基于 7.0.4）：`ciSenderName` 增加 `ownName` 参数，自己发送的消息不再留空——群聊里用「你在该群的成员名」（`groupInfo.membership.displayName`，与群资料页显示的一致，可与主资料不同名；为空时退回 `ownName`），私聊以及与自己的笔记聊天用个人资料名（`ownName`，`ChatView` 传入 `chatModel.currentUser.value?.profile?.displayName`）。频道消息没有个体发送者，保持无名字。
  - 这样导出的整段对话里每条消息都带署名，双向对话不会因为自己一侧没有名字而显得断裂；`ChatItem` 里本来就带有群成员信息，因此不需要新增请求或改动核心。
  - 改动文件：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/ChatModel.kt`（`ciSenderName`、`ownGroupMemberName`、`chatItemToText`、`chatItemsToText`）、`.../views/chat/ChatView.kt`（`copyItems` 传入自己的名字）。
  - 文档：`product/rules.md`（RULE-26 第 3 条改为「每条消息都署名，备注优先」）、`spec/client/chat-view.md`、`product/views/chat.md`、`plans/2026-11-02-own-name-and-time-format.md`。
  - 测试：`SelectedMessagesCopyTest` 扩充到 18 个用例（新增：自己的私聊消息带名字、群消息用群成员名、群成员名为空时退回个人资料名、笔记聊天也带名字），`./gradlew :common:desktopTest` 全部通过。差异补丁见 `diff/`。

- [x] 时间制式跟随系统的 12/24 小时制设置，并在设置里提供手动选项（跟随系统/12 小时制/24 小时制）
  - 已实现（基于 7.0.4）：新增客户端枚举偏好 `TimeFormat`（`SYSTEM`、`HOUR_12`、`HOUR_24`，默认 `SYSTEM`，键名 `TimeFormat`），设置页新增一行下拉框「时间制式」（`ic_schedule` 图标，选项文案 `time_format_system`/`12h`/`24h`）并带一行说明文字。
  - 「跟随系统」通过新增的 `expect fun systemUses24HourFormat()` 读取真实系统设置：Android 用 `android.text.format.DateFormat.is24HourFormat(androidAppContext)`（这是独立于语言的系统开关），桌面端读取默认语言短时间格式中的 `H`/`k`（24 小时）与 `h`（12 小时）。解析统一走纯函数 `uses24HourFormat(format, systemUses24Hour)`：显式选择优先，`SYSTEM` 才看系统。
  - 生效范围：`getTimestampText`（对话列表、消息时间、联系人请求等）与 `localTimestamp`（消息信息页、服务器统计等）的时间部分改为按 `timeOfDayPattern(use24Hour)` 输出 `HH:mm` 或 `h:mm a`（`localTimestamp` 保留秒，即 `HH:mm:ss` / `h:mm:ss a`）；日期部分仍用本地化格式，因此只有 12/24 制变化，其它排版不动。默认值 `SYSTEM` 与原行为一致，未改设置的用户看不到差别。
  - 立即生效：解析函数读取 `AppPreferences.timeFormat.state`，切换下拉框会让所有正在显示的时间重新组合，无需重启或重新拉取数据。
  - 不修改核心（Haskell）与网络协议，不修改 `AppSettings` 跨设备导入/导出结构（时间制式只是本机显示设置）。
  - 改动文件：`.../model/SimpleXAPI.kt`（`TimeFormat`、`AppPreferences.timeFormat`、`SHARED_PREFS_TIME_FORMAT`）、`.../model/ChatModel.kt`（`uses24HourFormat`、`timeOfDayPattern`、`timeOfDayPatternWithSeconds`、`preferredTimeFormatUses24Hour`、`getTimestampText`、`localTimestamp`）、新增 `.../platform/TimeFormat.kt` 与 `TimeFormat.android.kt`、`TimeFormat.desktop.kt`、`.../views/usersettings/SettingsView.kt`（下拉框）、`MR/base/strings.xml` 与 `MR/zh-rCN/strings.xml`（新增 5 条字符串）。
  - 文档：`product/rules.md`（RULE-27）、`product/concepts.md`（PC42）、`product/README.md`、`product/views/settings.md`（Time Format Section）、`spec/state.md`（`timeFormat`）、`spec/architecture.md`（expect/actual 表）、`spec/impact.md`、`spec/client/chat-view.md`、`plans/2026-11-02-own-name-and-time-format.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/TimeFormatTest.kt`（4 个用例：`SYSTEM` 跟随系统、显式选项覆盖系统、两种时间模式、默认值为 `SYSTEM`），`./gradlew :common:desktopTest` 全部通过（21 个测试类、137 个用例无失败），`:common:compileDebugKotlinAndroid` 也通过。差异补丁见 `diff/`。

- [x] 在聊天记录多选模式下加入像 QQ / 微信一样的「选择到此处」按钮，放在视图的顶端和底端
  - 实现方法：多选激活时，在消息列表的顶端（`FloatingDate` 下面）和底端（输入框上方）各显示一个圆角悬浮按钮（带向上／向下箭头 + `select_to_here` 文案）。点击后把「锚点消息 → 该边缘所在行」之间的所有消息加入当前选择；锚点就是用户最后一次手动选中的那条（`selectionAnchor`，按聊天 id 记忆，复选框遮罩和整行点击两条路径都会更新它），所以可以连续按多次逐步扩大范围；范围只增不减，误触再点一次同一条消息即可取消。
  - 行与消息的对应：列表渲染的是 `MergedItems`（同一发送者连续消息合并成一行、可折叠），范围以「行」为单位整行选取（`mergedItemChatItemIds`），因此折叠的群组会和手动点它一样被整组选中；行号取 `MergedItems.indexInParentItems`（即 LazyColumn 的行号），不是 `chatsContext.chatItems` 的下标。边缘行取自 `listState.layoutInfo.visibleItemsInfo`（列表是 `reverseLayout = true`，所以顶端是最大行号、底端是最小行号），保证选的是屏幕上真正看到的那一行。
  - 范围说明：没有选中项、没有锚点、或范围没有新增任何消息（`selectionToHereAddsItems`）时两个按钮都不渲染，避免出现空操作；纯客户端视图状态，未改核心、未新增偏好或 `AppSettings` 字段、未新增数据库写入。
  - 改动文件：新增 `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/ChatSelectionRange.kt`（`mergedItemChatItemIds`、`selectionRangeRows`、`selectionToHereIds`、`selectionToHereAddsItems`、`visibleEdgeRows` 纯函数）、`.../views/chat/ChatView.kt`（`selectionAnchor`、两处 `SelectToHereButton` 调用、`SelectToHereButton` 组合函数）、`MR/base/strings.xml` 与 `MR/zh-rCN/strings.xml`（新增 `select_to_here`）。
  - 文档：`product/views/chat.md`（多选工具栏表新增 Select to here 行与锚点说明）、`spec/client/chat-view.md`（Selection Mode / Select to here 条目）、`product/rules.md`（新增第 16 节 RULE-28）、`product/concepts.md`（PC43）、`spec/impact.md`（PC43 与 `views/chat/ChatSelectionRange.kt` 行）、`plans/2026-11-02-select-to-here-range.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/ChatSelectionRangeTest.kt`（9 个用例：单条消息的行只含一个 id、折叠组整组展开、锚点向上／向下的范围行、锚点行等于边缘行、没有锚点或边缘行未知时返回空、范围展开为合并行的全部 id、只补未选中的 id、反向列表的可见边缘行），`./gradlew :common:desktopTest` 全部通过（22 个测试类、146 个用例无失败）。差异补丁见 `diff/`。

- [x] 在 设置→外观→自定义主题→颜色调整 里加入「彩虹模式」开关（开启后界面颜色会一直循环彩虹变色，RGB 效果），颜色色块本身也跟着循环
  - 实现方法：新增 `ui/theme/Rainbow.kt`，用纯 Kotlin 的 HSV 转换实现 `shiftHue`（只改色相，保留饱和度、明度和透明度；灰、黑、白因为饱和度为 0 会被原样返回，保证背景和文字始终可读），再用同一个相位驱动 `rainbowColors`（`MaterialTheme` 调色板）与 `rainbowAppColors`（标题、`primaryVariant2`、发送／接收消息与引用颜色）。相位由 `rememberRainbowPhase()` 提供：开关关闭时返回 0、不启动计时器；打开时每 50ms 前进一次，16 秒走完一整圈（20 次/秒对这种慢速循环已经足够平滑，比逐帧重组便宜得多）。
  - 只影响显示：`SimpleXTheme` 把偏移后的颜色交给 `MaterialTheme` 和 `LocalAppColors`，`CurrentColors` 与已保存的 `ThemeOverrides` 一律不改，关掉开关就完全恢复用户自己挑的颜色；`SimpleXThemeOverride`（单人聊天主题预览、颜色编辑器预览）故意不参与，方便用户看到正在编辑的真实颜色。
  - 颜色调整里的 8 个色块也用同一相位着色，所以列表里的色块和界面颜色始终一致。
  - 改动文件：新增 `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/ui/theme/Rainbow.kt`；`.../ui/theme/Theme.kt`（`SimpleXTheme` 改用 `displayedColors` / `displayedAppColors`）；`.../views/usersettings/Appearance.kt`（`CustomizeThemeColorsSection` 新增开关行、说明页脚与动画色块）；`.../model/SimpleXAPI.kt`（新增 `rainbowThemeColors` 偏好，键 `"RainbowThemeColors"`，默认关闭）；`MR/base/strings.xml` 与 `MR/zh-rCN/strings.xml`（新增 `rainbow_mode`、`rainbow_mode_footer`）。
  - 兼容性：纯客户端显示开关，未改核心、未新增数据库写入、未加入 `AppSettings` 导出字段（核心会丢弃未知字段，且这种本机视觉效果不该被带到别的设备）。
  - 文档：`spec/services/theme.md`（新增 Rainbow mode 小节与源文件表行）、`spec/state.md`（UI 偏好表新增 `rainbowThemeColors`）、`product/rules.md`（新增第 17 节 RULE-29）、`product/concepts.md`（PC44）、`spec/impact.md`（PC44 与 `ui/theme/Rainbow.kt`、`Theme.kt`、`Appearance.kt` 行）、`product/views/settings.md`（外观表新增 Rainbow mode 行）、`plans/2026-11-02-rainbow-theme-mode.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/RainbowTest.kt`（13 个用例：RGB→HSV 的三原色与灰色、HSV→RGB 往返、`shiftHue` 保持饱和度／明度／透明度、色相按度数移动、360° 为一圈且可逆、无彩色不变、相位整圈回到起点、步长与周期一致、相位 0 时调色板不变、只偏移有彩色、`AppColors` 六个字段全部偏移且原对象不变、默认关闭），`./gradlew :common:desktopTest` 通过（23 个测试类、159 个用例无失败），`./gradlew :common:compileDebugKotlinAndroid` 编译通过。差异补丁见 `diff/`。

- [x] 彩虹模式增强：可调变色速度、聊天颜色可选择是否参与、消息文字也可彩虹（与聊天颜色互斥）、低性能模式（解决开启后手机端主页卡顿）
  - 实现方法：`Rainbow.kt` 把原来的固定常量拆成可配置的纯函数——`rainbowCycleMillis(speed)`（`SLOW` 32 秒、`NORMAL` 16 秒、`FAST` 8 秒）、`rainbowUpdateMillis(lowPerformance)`（50ms / 低性能 250ms）、`rainbowHueStep(cycleMillis, updateMillis)`，`nextRainbowPhase(phase, step)` 多了一个步长参数；`rememberRainbowPhase()` 读取速度与低性能开关，算出步长后只在两者变化时重启唯一的计时器（`LaunchedEffect(step, updateMillis)`），所以低性能模式不是“少画一点”，而是真的少重组 5 倍。
  - 聊天颜色可关：`rainbowAppColors(appColors, phase, chatColors)` 在 `chatColors` 为 false 时原样保留发送／接收气泡与引用颜色，只让标题、`primaryVariant2` 和界面调色板参与循环；`SimpleXTheme` 通过 `rememberRainbowChatColors()` 决定传什么给 `LocalAppColors`。
  - 消息文字彩虹：文字颜色本身没有色相可旋转（`onSurface` 在自带主题里是无彩色），所以 `rainbowTextColor(phase, dark)` 直接用相位生成颜色（深色主题饱和 0.55／明度 0.95，浅色主题 0.85／0.45，保证气泡上的对比度）；`MarkdownText` 的默认 `style` 改为 `rememberRainbowMessageTextColor() ?: MaterialTheme.colors.onSurface`，因此文本气泡、引用和消息详情都跟着变，调用点一个都不用改。
  - 互斥：`rainbowChatColorsActive(enabled, chatColors, messageText)` 在文字彩虹时强制返回 false（文字优先），设置里的两个开关也会在打开一个时自动关掉另一个，避免两个都彩虹导致看不清。
  - 设置界面：`CustomizeThemeColorsSection` 在彩虹开关下面新增「速度」下拉（`ExposedDropDownSettingRow` + `RainbowSpeed.entries`）与三个开关（聊天颜色、消息文字、低性能模式），以及两段页脚说明；这些行只在彩虹模式打开时出现。
  - 改动文件：`.../ui/theme/Rainbow.kt`（速度／低性能／文字颜色／互斥判定）、`.../ui/theme/Theme.kt`（气泡颜色开关）、`.../views/chat/item/TextItemView.kt`（`MarkdownText` 默认文字色）、`.../views/usersettings/Appearance.kt`（新增速度下拉与三个开关）、`.../model/SimpleXAPI.kt`（新增 `RainbowSpeed` 枚举与 `rainbowSpeed`、`rainbowChatColors`、`rainbowMessageText`、`rainbowLowPerformance` 四个偏好，键 `"RainbowSpeed"` / `"RainbowChatColors"` / `"RainbowMessageText"` / `"RainbowLowPerformance"`）、`MR/base/strings.xml` 与 `MR/zh-rCN/strings.xml`（新增 9 条字符串）。
  - 兼容性：仍是纯客户端显示偏好，未改核心、未新增数据库写入、未加入 `AppSettings`；`rainbowChatColors` 默认开启，所以对已经在用彩虹模式的人是纯增量，关掉才改变行为。
  - 文档：`spec/services/theme.md`（Rainbow mode 小节重写，新增速度／低性能／文字颜色／互斥的表格行与说明）、`spec/state.md`（`RainbowSpeed` 锚点与四个偏好行）、`product/rules.md`（RULE-29 新增第 6–8 条不变式并更新 Enforcement）、`product/concepts.md`（PC44 源文件）、`spec/impact.md`（`TextItemView.kt` 行加入 PC44）、`product/views/settings.md`（外观表新增四行）、`spec/client/chat-view.md`（Rainbow message text 条目）、`plans/2026-11-03-rainbow-options.md`。
  - 测试：`RainbowTest.kt` 扩到 19 个用例（新增：速度决定周期长度、低性能模式更新更慢但周期不变、快速档整圈仍回到起点、聊天颜色与文字互斥且文字优先、关掉聊天颜色时只保留界面循环、文字颜色随相位变化且在深浅主题下都保持可读、速度偏好可写入读回），`./gradlew :common:desktopTest` 通过（25 个测试类、179 个用例无失败），`./gradlew :common:compileDebugKotlinAndroid` 编译通过。差异补丁见 `diff/`。

- [x] 消息输入框发送键旁边放一个预览键，点击后打开一个界面显示该信息发送后的样子（渲染）（编辑 Markdown 消息时很有用）
  - 实现方法：新增 `views/chat/MessagePreviewView.kt`，`messagePreviewAvailable(text, inProgress)` 判断是否可以预览（有非空文本且不在发送中），`MessagePreviewButton` 是发送键左侧的 36dp 圆形眼睛图标（`ic_visibility`，次要色），点击后 `showMessagePreview(text)` 以全屏弹窗打开 `MessagePreviewView(text)`。
  - 渲染方式：弹窗里用聊天消息同一个渲染器（`MarkdownText` + `parseToMarkdown`）在 `Surface` 气泡中显示文本，所以“预览到的”就是“发送后的”格式，不会出现两套 Markdown 实现不同步的问题；底部用 `preview_message_footer` 说明这只是近似效果。
  - 只读、不发消息：预览只读取当前 `ComposeState.message.text` 的快照，不改输入框、不发消息、不写核心，关掉弹窗回到原来的草稿；语音消息（`ComposePreview.VoicePreview`）没有可渲染的文本，不显示该按钮。
  - 近似范围：@提及、链接和表情依赖具体会话（成员、链接模式、图片解析），预览里按普通 Markdown 显示，并在页脚写明，避免给出“完全一致”的错觉。
  - 改动文件：新增 `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/MessagePreviewView.kt`；`.../views/chat/SendMsgView.kt`（发送按钮所在行改为 `Row`，预览键放在发送键前面）；`MR/base/strings.xml` 与 `MR/zh-rCN/strings.xml`（新增 `preview_message`、`preview_message_footer`）。
  - 兼容性：纯客户端 UI，未改核心、未新增偏好与数据库写入、未加入 `AppSettings`；桌面端与 Android 共用同一套 Compose 代码。
  - 文档：`spec/client/compose.md`（新增第 8 节 Message Preview，源文件表两行）、`product/views/chat.md`（Compose Bar 表新增 Preview button 行）、`product/rules.md`（新增第 18 节 RULE-30）、`product/concepts.md`（PC45）、`spec/impact.md`（PC45 与 `SendMsgView.kt`、`MessagePreviewView.kt` 行）、`product/README.md`、`plans/2026-11-03-message-preview.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/MessagePreviewTest.kt`（4 个用例：普通文本可预览、空白文本不可预览、发送过程中不可预览、Markdown 文本可预览），`./gradlew :common:desktopTest` 通过（24 个测试类无失败），`./gradlew :common:compileDebugKotlinAndroid` 编译通过。差异补丁见 `diff/`。

- [x] 复制聊天消息的时候带上引用（回复）的内容，格式是在正文前面加上一段 `[引用：“$MSG”——$USER_NAME]`
  - 实现方法：`model/ChatModel.kt` 新增 `dirSenderName`（把 `ciSenderName` 的名字解析抽出来，引用消息带自己的方向，所以同一个函数也能算引用的作者）、`ciQuoteLine`（用 `ci.quotedItem` 拼出 `[引用：“…”——作者]`，没有引用或引用文本为空时返回 null）和 `ciTextWithQuote`（引用行 + 正文）；`chatItemToText` 的正文改用 `ciTextWithQuote`，所以多选复制是「名字, 时间」头行、引用行、正文三行。
  - 单条消息复制：`ChatItemView` 的 Copy 菜单把 `ciTextWithQuote(...)` 传给 `copyItemToClipboard`（该 `expect` 函数新增 `text` 参数，Android / 桌面两个 `actual` 相应改为写入传入文本；桌面的“有附件时复制文件路径”分支保持不变），因此单条复制也有引用行，但不会多出名字和时间头。
  - 格式：标记文字跟随界面语言（`copy_quote_with_author` / `copy_quote` 两个字符串），中文是 `[引用：“…”——张三]`，英文是 `[Quote: “…”——Alice]`；引用文本会折叠成一行（括号里放不下换行），但不截断内容。
  - 作者名字：引用的作者按**引用消息自己的方向**解析——引用了自己发的消息就写自己的名字（群聊里用该群的昵称，否则用资料名），群成员用其显示名（本地别名优先），频道消息没有作者时只保留 `[引用：“…”]`。
  - 未改核心与 `AppSettings`，未新增偏好或数据库字段；消息信息页里“按历史版本复制”的入口故意不动（那里复制的是某个历史版本的正文，不是一条完整消息）。
  - 文档：`product/rules.md`（RULE-26 新增第 6 条）、`spec/client/chat-view.md`（复制小节）、`product/views/chat.md`（单条 Copy 与多选 Copy 两行）、`spec/impact.md`（`ChatItemView.kt` 行）、`plans/2026-11-03-copy-with-quote.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/CopiedQuoteTest.kt`（10 个用例：引用行在正文前、引用行在多选复制里位于名字时间之后、引用自己的消息用自己名字、群引用用成员显示名、引用文本折叠成一行、没有引用时行为不变、引用为空文本时跳过、频道引用没有作者、多选复制整体格式、标记来自字符串资源），`./gradlew :common:desktopTest` 通过（25 个测试类无失败），`./gradlew :common:compileDebugKotlinAndroid` 编译通过。差异补丁见 `diff/`。

- [x] 应用内聊天翻译：长按任意消息气泡即可翻译并以双语对照显示，输入框发送键旁新增翻译键，设置页可选翻译服务商并配置密钥，自动翻译当前视区消息并按速率限制批量处理
  - 已实现（基于 7.0.4）：消息菜单翻译——长按/右键任意消息气泡，弹出菜单新增「翻译」项（图标 `ic_translate`），点击后自动识别源语言并翻译；译文以「原文 + 译文」双语气泡在该消息下方追加显示，属非破坏性展示，不修改原消息内容。
  - 已实现（基于 7.0.4）：发送键旁翻译键——输入框发送键左侧新增 36dp 圆形翻译键（`ic_translate`）。单击把输入框当前文本翻译为默认目标语言（默认英语）；长按弹出语言选择菜单，选中后本次翻译为该语言（仅本次有效，不改默认设置）；翻译后再点一次撤销回原文，若翻译后又编辑了文本则不可撤销、此时点击改为重新翻译（是否可撤销由 `ComposeState` 新增的 `translateOriginal`/`translateResult` 字段判断）。
  - 已实现（基于 7.0.4）：设置页——设置新增翻译设置界面（标题 `translation_settings`，即「翻译消息 / Translate messages」），可配置服务提供商 provider（Google 免费、Bing 免费、百度（需 API Key）、AI 翻译（OpenAI Responses 协议，默认 DeepSeek）、自定义（OpenAI 兼容端点，可自定义 base URL/模型/密钥））、默认目标语言 targetLang、自动翻译开关 autoTranslate，以及各提供商的 API Key。
  - 已实现（基于 7.0.4）：密钥与端点——百度另存 AppID/密钥；AI 提供方默认 base URL `https://api.deepseek.com/v1`、模型 `deepseek-chat`，自定义方（custom）的 base URL/模型默认为空、由用户自填。所有密钥先用 `Cryptor` 加密再存本机，不随备份导出。
  - 已实现（基于 7.0.4）：自动翻译与限流——autoTranslate 只翻译当前视区可见消息（由当前视区可见项集合的变化触发，带 400ms 防抖；已翻译或正在翻译的消息不重复请求）；按速率限制批量处理：请求间隔 `requestIntervalMs`（默认 600）、最大并发 `maxConcurrent`（默认 2）、单次批量上限 `batchLimit`（默认 20）；手动翻译同样受最小间隔约束，防止被服务商封禁或耗尽余额。
  - 已实现（基于 7.0.4）：纯客户端本机存储——所有配置存本机 `AppPreferences`（JSON 字符串偏好 `translationConfig`，密钥加密存 `translationSecrets` + `translationSecretsIv`），**不进入 `AppSettings` 导出**；不修改核心（Haskell）与网络协议，便于日后跟随官方版本升级。
  - 改动文件：新增 `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/translation/`（`TranslationModels.kt`、`TranslationLang.kt`、`TranslationApi.kt`、`TranslationHttp.kt`（`expect` suspend httpGetText/httpPostJson）+ desktop/android actual、`TranslationService.kt`）；`.../model/SimpleXAPI.kt`（`AppPreferences` 的四个翻译偏好与 `SHARED_PREFS_*` 常量）、`.../views/chat/ComposeView.kt`（`ComposeState.translateOriginal`/`translateResult`）；聊天侧 UI（消息菜单项、发送键翻译键、气泡译文、自动翻译可见区）在 `.../views/chat/`（`SendMsgView.kt`、`ChatItemView.kt`、`FramedItemView.kt` 及翻译界面），设置侧在 `.../views/usersettings/SettingsView.kt` 与 `TranslationSettingsView.kt`；`MR/base/strings.xml` 与 `MR/zh-rCN/strings.xml`。
  - 文档：`product/concepts.md`（PC46）、`product/rules.md`（RULE-31）、`product/README.md`、`product/views/{chat,settings}.md`、`spec/impact.md`（PC46）、`spec/state.md`、`spec/client/{chat-view,compose,translation}.md`、`plans/2026-11-04-in-app-chat-translation.md`。

## mod:

none

## bug fix:

- [x] 彩虹模式：聊天页面里的气泡颜色不变色，只有自定义主题页面的预览会变色
  - 原因：`SimpleXThemeOverride` 只把原始配色交给 `MaterialTheme` / `LocalAppColors`，完全没有套用彩虹相位；而聊天页面整体就包在它里面（`ChatView.kt` 用 `SimpleXThemeOverride(overrides ?: CurrentColors...)` 包住整个会话），所以设置页的色块会变色，真正进入聊天后气泡与界面色都不刷新。
  - 附带问题：`rememberRainbowPhase()` 原来每个调用点各起一个计时器；`MarkdownText` 的默认文字色也调用它，于是聊天里每条消息都会各开一个协程，既不必要又会互相不同步，也与“手机端主页卡顿”有关。
  - 修复方法：相位改为由 `SimpleXTheme` 统一提供——新增 `LocalRainbowPhase`（`compositionLocalOf { 0f }`），`SimpleXTheme` 里原来那个计时器改名为 `rememberRainbowPhaseTimer()`（内部函数，只此一处运行计时器），并把结果通过 `LocalRainbowPhase` 下发；`rememberRainbowPhase()` 变成只读该 CompositionLocal，不再起计时器。
  - `SimpleXThemeOverride` 现在同样读取相位，并用 `rainbowColors` / `rainbowAppColors` 换算出本屏的 `displayedColors` / `displayedAppColors` 后再交给 `MaterialTheme` 与 `LocalAppColors`，因此会话页的界面色与气泡会像设置页预览一样实时循环；它仍然只改“显示”，不改每聊天的主题与已保存的 `ThemeOverrides`。
  - 改动文件：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/ui/theme/Rainbow.kt`（新增 `LocalRainbowPhase`、`rememberRainbowPhaseTimer`，`rememberRainbowPhase` 改为读 CompositionLocal）、`.../ui/theme/Theme.kt`（`SimpleXTheme` 下发相位，`SimpleXThemeOverride` 套用相位）。
  - 兼容性：纯显示层修复，未改核心、未新增偏好或 `AppSettings` 字段，也不改变任何已保存颜色。
  - 文档：`spec/services/theme.md`（Rainbow mode 表格新增 `LocalRainbowPhase` / `rememberRainbowPhaseTimer`，并说明相位必须来自唯一计时器、`SimpleXThemeOverride` 也套用相位）、`product/rules.md`（RULE-29 第 2、6 条与 Enforcement 更新）、`product/concepts.md`（PC44 源文件）、`spec/impact.md`（`Theme.kt`、`Rainbow.kt` 行）、`plans/2026-11-03-fix-rainbow-in-chat.md`。
  - 测试：`./gradlew :common:desktopTest` 全部通过（25 个测试类、179 个用例无失败），`./gradlew :common:compileDebugKotlinAndroid` 编译通过。差异补丁见 `diff/`。

- [x] 群成员连接率似乎忘记排除自己了，导致永远无法达到百分之一百
  - 原因：`groupMembersConnection` 原来写的是 `(members.count { it.ready } + if (currentMembership.ready) 1 else 0) to (members.size + 1)`。而 `GroupMember.ready` 等于 `activeConn?.connStatus == ConnStatus.Ready`，当前用户自己的 `GroupInfo.membership` 里通常没有 `activeConn`（连接信息只存在于成员列表的条目上，而成员列表里并不包含自己），所以分子里的 `+1` 永远是 0、分母里的 `+1` 却永远算上，比率就永远差一个人，最多只能到 `(N-1)/N`。
  - 修复方法：把「自己」从成员列表里先过滤掉（`members.filter { it.groupMemberId != currentMembership.groupMemberId }`，这样即使某个列表里包含了自己也不会重复计数），总数仍然把自己算进去（保持与标题里的 `N members` 一致），而自己是否有连接改为：有 `activeConn` 时按严格的 `ready` 判断，没有 `activeConn` 时按 `memberActive`（成员状态是否活跃）判断——客户端能读到成员列表本身就说明自己已经连上了，所以合理的群在全员联通时会显示 `N/N (100%)`。
  - 改动文件：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupChatInfoView.kt`（`groupMembersConnection` 及注释）；测试 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/GroupMembersConnectionRateTest.kt`。
  - 未改核心与 `AppSettings`，未新增字符串；群聊列表里的圆点与群信息页共用同一个函数，因此两处结果必然一致。
  - 文档：`product/rules.md`（RULE-21 第 2 条）、`product/views/group-info.md`（Connection rate 行）、`spec/client/chat-view.md`（§8 成员列表）、`plans/2026-10-22-fix-group-rate-self.md`。
  - 测试：`GroupMembersConnectionRateTest` 扩充到 13 个用例（成员使用不同 `groupMemberId`；新增「自己只被计一次」「自己不活跃时不算已连接」「达到 100%」等），`./gradlew :common:desktopTest` 全部通过（13/13，全部 17 个测试类无失败）。差异补丁见 `diff/`。

- [x] 点的显示有时候和联系人页面里显示的连接状态不一致
  - 原因：同一个连接的状态在两个地方来自不同的数据源。圆点（主页列表、私聊标题、群成员列表）读的是 `connectionSubStatus(conn, chatModel.connectionSubStatuses)`（事件缓存，其次才是上次拉取的 `ConnectionStats.subStatus`）；而联系人页面的「服务器 → 网络状态」行读的是 `chatModel.chatSubStatus`——这个状态只在「打开某个聊天」或「收到属于当前打开聊天的订阅事件」时才会被写入，聊天关闭时还会被清空，与页面上显示的那个连接并不对应；群成员信息页又读自己临时拉取的 `cStats.subStatus`。三者来源不同，所以只要其中一处有值另一处没有（例如 `CR.ContactConnected` 只写了 `chatSubStatus` 而没写逐连接缓存），显示就会不一致。
  - 修复方法：新增统一解析器 `ChatModel.connectionStatus(conn)`（内部是纯函数 `connectionStatus(conn, liveStatuses, openChatAgentConnId, openChatStatus)`），优先级为「事件里该连接的最新状态 → 该连接上次拉取的 `ConnectionStats.subStatus` → 当前打开聊天的状态（且仅当 `conn.agentConnId == chatAgentConnId`，避免串到别的聊天）」。主页列表、私聊标题、群成员列表、联系人信息页、群成员信息页全部改为读它；后两者保留自己的 `cStats.subStatus` 仅作为解析器还未知时的兜底。
  - 改动文件：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/ChatModel.kt`（`ChatModel.connectionStatus` + 纯函数）、`.../views/chat/ChatView.kt`（`ChatInfoToolbarTitle`）、`.../views/chat/ChatInfoView.kt`（联系人页网络状态行）、`.../views/chat/group/GroupChatInfoView.kt`（`MemberRow`）、`.../views/chat/group/GroupMemberInfoView.kt`（成员信息页网络状态行）、`.../views/chatlist/ChatPreviewView.kt`（`chatPreviewTitle`）。
  - 未改核心、未新增字符串、未新增偏好或 `AppSettings` 字段；状态仍是会话内的内存缓存，不落盘。
  - 文档：`product/rules.md`（RULE-22 新增「全应用统一状态源」条款及条目）、`product/views/contact-info.md`、`product/views/chat.md`、`product/concepts.md`（PC37）、`spec/state.md`、`spec/client/chat-view.md`、`spec/client/chat-list.md`、`spec/impact.md`、`plans/2026-10-22-fix-status-dot-source.md`。
  - 测试：扩充 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/ConnectionSubStatusTest.kt` 到 12 个用例（新增「能取到当前打开聊天的状态」「不会把别的聊天的状态拿来用」「没有连接时忽略打开聊天的状态」「事件状态优先于打开聊天的状态」「`ConnectionStats` 优先于打开聊天的状态」），`./gradlew :common:desktopTest` 全部通过（12/12，全部 17 个测试类无失败）。差异补丁见 `diff/`。

- [x] 程序刚启动的时候连线连接状态的那个点不会显示，需要手动点进去一次才行
  - 原因：客户端的连接状态只有两个来源——① 核心的 `CEvtSubscriptionStatus` 事件（只在服务器的订阅状态**发生变化**时才发；启动时已经连上的连接没有任何变化，因此不会发事件）；② `apiContactInfo` 返回的 `ConnectionStats.subStatus`（以前只有在打开聊天 `ChatView` 或打开信息页时才会去拉）。所以刚启动时逐连接状态表是空的、也没有任何连接拉过 stats，解析器对每个连接都返回 `null`，按「没有状态就不画点」的规则所有点都不显示；打开一次聊天后才会补上那一个连接。
  - 修复方法：新增纯函数 `chatsForConnectionStatus(chats, requested, statusKnown)` 筛选出「联系人 ready && active、连接状态仍未知、本会话尚未请求过该 `agentConnId`」的私聊；在 `ChatListView` 里用一个 `LaunchedEffect`（依赖私聊 id 列表）把这些连接的 `agentConnId` 记进 `ChatModel.connectionStatusRequested`，然后在 `withBGApi` 中逐个调用 `apiContactInfo`（与打开聊天时完全相同的一次调用，保证数值同源），结果用 `ChatsContext.updateContactConnectionStats` 写入模型，解析器即可通过 `Connection.connectionStats.subStatus` 读到。请求异常会被 try/catch 记录并吞掉（例如联系人已被删除），该连接本会话不再重试，点保持隐藏。
  - 改动文件：`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/ChatModel.kt`（`connectionStatusRequested`、`chatsForConnectionStatus`）、`.../views/chatlist/ChatListView.kt`（后台预取）。
  - 未改核心、未新增字符串/偏好/`AppSettings` 字段，仍是会话内内存状态，不落盘；订阅事件到来时仍会覆盖这些值。
  - 范围说明：只对私聊连接做预取（每个联系人本会话最多一次）。群成员的圆点仍需等订阅事件或打开该成员信息页，因为按成员逐个请求在大群里会是一次打开就几十上百次调用；如果之后需要，可在这个基础上再加按群成员的懒加载。
  - 文档：`product/rules.md`（RULE-22 新增第 5 条「状态本会话只请求一次且在后台」）、`product/views/chat-list.md`、`product/concepts.md`（PC37）、`spec/client/chat-list.md`（Connection Status Dot）、`spec/state.md`（`connectionStatusRequested`）、`spec/impact.md`、`plans/2026-10-22-fix-status-dot-startup.md`。
  - 测试：新增 `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/ConnectionStatusPrefetchTest.kt`（9 个用例：需要预取的私聊／状态已知／已请求过／无连接／未 ready／未 active／群聊／混合场景只返回待请求的／每个连接都参与判断），`./gradlew :common:desktopTest` 全部通过（9/9，全部 18 个测试类共 107 个用例无失败）。差异补丁见 `diff/`。

- [ ] 好像有性能问题，在主页滚动列表会很卡

## del:

none
