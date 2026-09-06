# CC: Tweaked 1.120.2 新功能

若干错误修复：
* 修复 `read()` 不产生 yielded 时 shell 崩溃的问题。
* 修复口袋计算机/打印输出讲台在屏幕最底部时不渲染的问题。
* 若干文档修复。(tomodachi94)

# CC: Tweaked 1.120.0 新功能

* 支持在 `parallel.waitForAll` 中生成新的并行函数。

一个错误修复：
* 使 HTTP IP 过滤更严格。

# CC: Tweaked 1.119.0 新功能

* 添加 `commands.getDimension()`。
* 添加 `cc.base64` 模块。
* 更新 Cobalt 至 0.9.9，带来若干 Lua 5.5 变更：
  * 浮点数现在会以足够的位数打印以正确往返。
  * 添加 `table.create`。
  * `utf8.offset` 现在返回代码点的最终位置。

若干错误修复：
* 修复 `LuaTable` 中整数索引的处理。
* 修正 `os.time` 中 `min` 和 `sec` 的默认值。(sircfenner)
* 使 HTTP IP 过滤更严格。

# CC: Tweaked 1.118.0 新功能

* 为 `rednet.lookup` 添加超时参数。
* 添加 `commands.getEntity`。

若干错误修复：
* 文档修复 (ItsNotVingtdeux)。
* 修复根目录外 shebang 的处理 (graypinkfurball)。
* 修复终端调整大小后 `edit` 未清除菜单的问题 (Wojbie)。

# CC: Tweaked 1.117.1 新功能

若干错误修复：
* 若干文档修复 (MarianoAlipi, Pokebrouserkat)。
* 修复流体带有 NBT 时 `pushFluid`/`pullFluid` 不工作的问题 (UQuark)。
* 限制 `speaker.playSound` 的声音长度。
* 移除令人困惑的"ComputerCraft 可能安装不正确"消息。

# CC: Tweaked 1.117.0 新功能

* 支持讲台上的口袋计算机鼠标输入。
* 讲台上的口袋计算机现在附加下方的外设。
* 为方块和物品详情添加地图颜色 (ShreksHellraiser)。
* 为物品详情添加药水效果。
* 为 websocket 句柄添加 `getResponseHeaders` 方法。
* 更新翻译。

若干错误修复：
* 许多文档修复 (McJack123, tomodachi94)。
* 修复使用 Building Gadgets 放置 CC 方块时崩溃的问题。
* 修复红石继电器在区块加载时未更新红石输入/输出的问题。
* 修复 Windows 上处理 `. .` 的不一致问题。
* 修复 MoreRed 时 bundled 线缆输入未更新的问题。
* 修复因错误关闭 socket 时 `websocket_closed` 不总是被关闭的问题。

# CC: Tweaked 1.116.2 新功能

若干错误修复：
* 更新 Create 兼容至 Create Fabric 6.0。
* 各种文档修复 (Zirunis)。
* 修复与 Inventorio 的崩溃问题。
* SNBT 解析的各种修复。
* 修复字符串模式匹配中的 Regex DDoS 问题。

# CC: Tweaked 1.116.1 新功能

* 更新翻译。

一个错误修复：
* 修复 mcfunction 文件包含 CC 命令时的 NPE 问题。

# CC: Tweaked 1.116.0 新功能

* 添加 `turtle.getEquippedLeft()` 和 `turtle.getEquippedRight()`。
* 为软盘和口袋计算机添加物品标签。
* 在 `edit` 中支持多行字符串和注释。

若干错误修复：
* 在使用 Pojav 时忽略着色器编译错误。
* 修复字符输入的几个问题。
* 修复装备/卸下升级时口袋计算机染料丢失的问题。
* 修复分配追踪的冗余警告。
* 修复 `__lt`/`__le` 在异构类型上不工作的问题。
* 许多文档修复 (Lemmmy, matematikaadit, McJack123)。
* 修复 `window` 和 `colour.toBlit` 中将 `0` 视为有效颜色的问题。
* 修复粘贴过长文本时的越界问题。
* 修复字符串转义的颜色高亮问题 (LorneHyde)。
* 修复高级计算机侧边栏纹理偏移问题。

# CC: Tweaked 1.115.1 新功能

* 更新各种翻译 (cyb3r, kevk2156, teamer337, yakku)。
* 支持 Fabric 的物品查找 API 用于注册媒体提供者。

若干错误修复：
* 修复 Create 6.0 上的崩溃问题 (ellellie)。
* 修复 `speaker.playAudio` 未更新扬声器音量的问题。
* 调整口袋讲台纹理大小以修复生成 mipmap 时的问题。

# CC: Tweaked 1.115.0 新功能

* 支持将口袋计算机放在讲台上。
* 在 `nil` 错误时建议替代表键。
* 并行函数内部的错误现在附加了源信息。
* 向 Java API 暴露打印输出内容。

若干错误修复：
* 忽略 `char`/`paste` 事件中不可表示的字符。

# CC: Tweaked 1.114.4 新功能

* 允许在 CC 字符集中输入/粘贴任何字符。

若干错误修复：
* 修复命令计算机作为外设暴露的问题（仅 Forge）。
* 修复命令计算机在 Create 装置中放置时设置了 NBT 的问题。
* 在检查海龟移动中的实体时使用正确的边界框。

# CC: Tweaked 1.114.3 新功能

* `wget` 现在打印发生的错误，而不是通用的"Failed"(tizu69)。
* 更新几个翻译。

若干错误修复：
* 修复 `fs.isDriveRoot` 对不存在的文件返回 true 的问题。
* 修复发送终端内容时可能的内存泄漏。

# CC: Tweaked 1.114.2 新功能

一个错误修复：
* 修复渲染空监视器时的 OpenGL 错误。

# CC: Tweaked 1.114.1 新功能

若干错误修复：
* 修复监视器触摸事件仅从一个监视器触发的问题。
* 修复讲台没有物品时崩溃的问题。
* 修复监视器上光标不闪烁的问题。

# CC: Tweaked 1.114.0 新功能

* 添加红石继电器外设。
* 添加对 `math.atan(y, x)` 的支持。
* 更新几个翻译。

若干错误修复：
* 修复制作后口袋升级未出现的问题。
* 收到消息后取消 `rednet.receive` 和 `Websocket.receive` 计时器。
* 修复解析和打印大双精度数的一些问题。
* 修复切换维度后手持口袋计算机为空白的问题。

# CC: Tweaked 1.113.1 新功能

* 更新日文翻译 (konumatakaki)。
* 提高 `textutils.urlEncode` 的性能。

若干错误修复：
* 修复从 Java 到 Lua 转换递归对象时溢出问题。
* 修复 Forge 下 websocket 压缩不工作的问题。

# CC: Tweaked 1.113.0 新功能

* 允许在讲台上放置打印的页面和书籍。

若干错误修复：
* 各种文档修复 (MCJack123)
* 修复计算机和海龟被 TNT 爆炸时未掉落的问题。
* 修复海龟挖掘方块时被破坏时崩溃的问题。
* 修复手持口袋计算机终端在副手时未更新的问题。

# CC: Tweaked 1.112.0 新功能

* 在使用 `!` 代替 `not` 时报告自定义错误。
* 更新几个翻译 (zyxkad, MineKID-LP)。
* 添加 `cc.strings.split` 函数。

若干错误修复：
* 修复 `drive.getAudioTitle` 在未插入磁盘时返回 `nil` 的问题。
* 升级口袋计算机时保留物品数据。
* 为 `cc.strings.wrap` 添加缺失的边界检查 (Lupus950)。
* 修复 Create 装置移动时调制解调器不移动的问题。

# CC: Tweaked 1.111.0 新功能

* 更新几个翻译 (Ale32bit)。
* 将海龟纹理拆分为单独的纹理。
* 为 `io` 库添加 `r+`/`w+` 支持。
* 在安装 Optifine 时警告功能未注册。

若干错误修复：
* 允许在"冒险"中使用木板建造 (dan200)。
* 修复 `disk.getAudioTitle()` 对某些 mod 唱片返回未翻译字符串的问题。
* 修复旁观者模式下右键海龟时崩溃的问题。

# CC: Tweaked 1.110.3 新功能

* 更新几个翻译 (PatriikPlays)。

若干错误修复：
* 修复某些错误缺少源位置的问题。
* 正确处理多个线程同时发送 websocket 消息。

# CC: Tweaked 1.110.2 新功能

* 添加 `speaker sound` 命令 (fatboychummy)。

若干错误修复：
* 改进调用无路径 `speaker play` 时的错误 (fatboychummy)。
* 阻止使用 `speaker.playSound` 播放音乐唱片。
* 各种文档修复 (cyberbit)。
* 修复 Forge 上某些库存无法传输到通用外设的问题。
* 修复手持口袋计算机时罕见的崩溃问题。
* 修复 Create 移动时调制解调器损坏的问题。
* 修复通过 Immersive Portals 传送门渲染海龟时崩溃的问题。

# CC: Tweaked 1.110.1 新功能

若干错误修复：
* 修复计算机在卸载/不 tick 一段时间后未开机的问题。
* 修复 Forge 上网络线缆有时不连接的问题。

# CC: Tweaked 1.110.0 新功能

* 在 `/computercraft` 命令中添加新的 `@c[...]` 语法用于选择计算机。
* 移除 Forge 上调制解调器的自定义破坏进度。

若干错误修复：
* 修复客户端和服务器 DFPWM 转换器不同步的问题。
* 修复 `turtle.suck` 失败时报告不正确错误的问题。
* 修复口袋计算机为错误的计算机显示状态（闪烁、调制解调器灯）的问题。
* 修复将无效 BE 包装为通用外设时崩溃的问题。
* 箱子外设在箱子转换为双箱子时现在重新附加。
* 修复 `speaker` 程序未解析相对于当前目录的文件的问题。
* 外设分离时跳过主线程任务。
* 修复在 `__tostring` 内 yield 时的内部 Lua VM 错误。

# CC: Tweaked 1.109.7 新功能

* 提高移除和卸载有线线缆/调制解调器的性能。

若干错误修复：
* 修复区块卸载和重新加载时监视器有时不在客户端更新的问题。
* `colour.toBlit` 正确地对越界值报错。
* 在 `window` 中如 `term.native()` 那样对非标准颜色进行四舍五入。
* 修复客户端监视器同时渲染当前和过时内容的问题。

# CC: Tweaked 1.109.6 新功能

* 改进几个 Lua 解析器错误消息。
* 允许附加 mod 注册 `require` 模块。

若干错误修复：
* 修复弱表在 key 被 GC 时变得格式不正确的问题。

# CC: Tweaked 1.109.5 新功能

* 添加新的 `/computercraft-computer-folder` 命令用于在单人游戏中打开计算机的文件夹。

若干错误修复：
* 关闭 `edit` 的"运行"屏幕时丢弃正在输入的字符。

# CC: Tweaked 1.109.4 新功能

若干错误修复：
* 当计算机分配零字节时不记录警告。
* 修复命令计算机 NBT 转换中的错误列表索引 (lonevox)。
* 修复 `endPage()` 未更新打印机的方块状态。
* 若干文档改进 (znepb)。
* 在计算机启动前正确挂载磁盘，而不是之后。
* 更新到 Cobalt 0.9
  * 调试钩子现在对每个函数正确调用。
  * 修复 `debug.getinfo` 的几个小的不一致问题。
  * 修复从可变参数创建时 Lua 表大小不正确的问题。

# CC: Tweaked 1.109.3 新功能

* 命令计算机现在显示在创造模式物品栏的操作员物品标签页中。

若干错误修复：
* 如果一次发送太多 websocket 消息则报错。
* 修复方法调用上尾随逗号（如 `x:f(a, )`）未使用我们的自定义错误消息的问题。
* 修复在 `if` 块中首次使用 `goto` 时的内部编译器错误。
* 修复添加和删除键时表哈希部分的大小调整不正确的问题。

# CC: Tweaked 1.109.2 新功能

* `math.random` 现在使用 Lua 5.4 的随机数生成器。

若干错误修复：
* 修复涉及 `goto` 语句的错误行号错误的问题。

# CC: Tweaked 1.109.1 新功能

若干错误修复：
* 修复右键和中键鼠标按钮不触发 `mouse_drag` 事件的问题。
* 修复语法错误涉及 `goto` 或 `::` 时崩溃的问题。
* 修复添加/删除观察者时发生死锁的问题。
* 允许用 `turtle.place()` 将种子放入堆肥桶。

# CC: Tweaked 1.109.0 新功能
重大变更：
* 更新到 Lua 5.2
  * `getfenv`/`setfenv` 现在仅对 Lua 函数有效。
  * 添加对 `goto` 的支持。
  * 移除对转储和加载二进制块的支持。
* 文件句柄、HTTP 请求和 websocket 消息现在使用原始字节而不是转换为 UTF-8。

新功能：
* 为 `textutils.serialiseJSON` 添加 `allow_repetitions` 选项。
* 追踪计算机分配的内存。
* 将 `os.version()` 返回的版本更新为 `CraftOS 1.9`。

若干错误修复：
* 修复使用位置捕获和字符串模式中的反向引用时出错的问题（如 `()(%1)`）。
* 修复用 `%d` 格式化非实数的问题。

# CC: Tweaked 1.108.4 新功能

* 重写 `@LuaFunction` 生成以使用 `MethodHandle` 而不是 ASM。
* 重构 `ComputerThread` 以提供更清晰的接口。
* 移除 `disable_lua51_features` 配置选项。
* 更新几个翻译 (Sammy)。

若干错误修复：
* 修复在破坏和更换监视器后监视器外设变为"分离"的问题。
* 修复放置时告示牌为空的问题。
* 修复挂载错误消息的几个不一致问题。

# CC: Tweaked 1.108.3 新功能

若干错误修复：
* 修复加入专属服务器时断开连接的问题。

# CC: Tweaked 1.108.2 新功能

* 为有线调制解调器应忽略的方块添加标签。

若干错误修复：
* 修复调整大小后监视器有时变形的问题。
* 修复头骨配方使用了错误的 UUID 格式的问题。
* 修复终端调整大小后绘画画布不总是重绘的问题。

# CC: Tweaked 1.108.1 新功能

若干错误修复：
* 阻止无操作玩家破坏或放置命令计算机。
* 允许在子类加载器中定义的类上使用 `@LuaFunction` 注解的方法。

# CC: Tweaked 1.108.0 新功能

* 从终端/监视器数据包中移除压缩。Vanilla 应用了自己的压缩，所以这最终没有预期的那么有帮助。
* `/computercraft` 命令现在支持权限 mod。
* 将一些 GUI 纹理拆分为精灵表。
* 在字符串模式匹配中支持 `%g` 字符类。

若干错误修复：
* 修复通过磁盘驱动器播放某些 mod 唱片时崩溃的问题。
* 修复计算机连接或分离到监视器时的竞态条件。
* 修复"最大 websocket 消息"配置选项未被读取的问题。
* `tostring` 现在正确遵守 `__name`。
* 修复模式匹配字符类的几个不一致问题。

# CC: Tweaked 1.107.0 新功能

* 添加 `disabled_generic_methods` 配置选项以禁用通用方法。
* 添加与 EMI 的基本集成。
* 附魔的海龟工具现在带有闪烁效果渲染。
* 更新几个翻译 (PatriikPlays, 1Turtle, Ale32bit)。

若干错误修复：
* 修复在专属服务器上生成客户端配置文件的问题。
* 修复以"f"或"d"结尾的数字被视为有效的问题。
* 修复 `string.pack` 的"z"说明符导致越界错误的问题。
* 修复 `turtle.dig` 自定义操作的几个问题（耕作、制作路径）。

# CC: Tweaked 1.106.1 新功能

若干错误修复：
* 默认阻止 CGNAT 范围 (100.64.0.0/10)。
* 修复与其他 mod 替换触及距离的冲突。

# CC: Tweaked 1.106.0 新功能

* 大量文档改进 (MCJack123, znepb, penguinencounter)。
* 将 `fs.find` 移植到 Lua。这也允许使用 `?` 作为通配符。
* 计算机光标现在在黑暗中发光。
* 允许从 GUI 更改海龟升级。
* 添加将 Unicode 字符串序列化为 JSON 的选项 (MCJack123)。
* `window` API 的小优化。
* 海龟升级现在可以保留升级物品堆栈的 NBT 和破坏时的 NBT。
* 添加通过数据包支持工具附魔和耐久度的支持。这对内置工具是禁用的。

若干错误修复：
* 修复海龟倒置时渲染不正确的问题。
* 修复对 `IArguments.escapes` 的错误调用。
* Lua REPL 不再接受 `)(` 作为有效表达式。
* 修复 Lua REPL 中 `require`/`package.path` 的几个不一致问题 (Wojbie)。
* 修复海龟能够将其水桶放置在触及距离外的问题。
* 修复 `$private` 规则未阻止的几个私有 IP 范围。
* 改进 `/computercraft` 命令中的权限检查。

# CC: Tweaked 1.105.0 新功能

* 优化 JSON 字符串解析。
* 添加 `colors.fromBlit` (Erb3)。
* 上传文件大小限制现在是可配置的 (khankul)。
* 有线线缆不再有距离限制。
* Java 方法现在与 Lua 一致地强制转换值为字符串。
* 为 HTTP API 添加自定义超时支持。
* 支持 HTTP 请求的自定义代理 (Lemmmy)。
* `speaker` 程序在播放 HTML 文件时现在报错。
* `edit` 在编辑只读文件时现在显示错误消息。
* 更新乌克兰语翻译 (SirEdvin)。

若干错误修复：
* 允许 GPS 主机仅相距 1 个方块。
* 修复计算机 GUI 中"开机"/"关机"按钮反转的问题 (Erb3)。
* 修复打印输出 UI 中方向键不工作的问题。
* 若干文档修复 (zyxkad, Lupus590, Commandcracker)。
* 修复 Forge 上监视器渲染器调试文本总是可见的问题。
* 修复另一个 mod 更改 LoggerContext 时崩溃的问题。
* 修复 `monitor_renderer` 选项在 Fabric 配置文件中不存在的问题。
* MacOS/OSX 上的粘贴现在使用 Cmd+V 而不是 Ctrl+V。
* 修复海龟在 y<0 时倒置放置方块的问题。

# CC: Tweaked 1.104.0 新功能

* 更新到 Minecraft 1.19.4。
* 海龟现在可以右键点击物品"进入"某些方块（默认情况下是炼药锅和蜂箱，可通过 `computercraft:turtle_can_use` 方块标签配置）。
* 更新 Cobalt 至 0.7：
  * `table` 方法和 `ipairs` 现在使用元方法。
  * 类型错误现在使用 `__name` 元标签。
  * 协程不再在多个线程上运行。
  * 超时错误应该更可靠地抛出。
* `speaker` 程序现在在常见不支持的音频格式上报告错误。
* `multishell` 现在隐藏其终端重定向的实现细节。
* 默认使用 VBO 监视器渲染器。
* 改进缺少逗号时表格和参数列表尾随逗号的语法错误。
* 海龟现在可以持有旗帜。
* 更新几个翻译 (Alessandro, chesiren, Erlend, RomanPlayer22)。

若干错误修复：
* `settings.load` 现在忽略手动编辑 `.settings` 文件造成的格式错误值。
* 修复 `os.cancelAlarm` 和 `os.cancelTimer` 的介绍日期 (MCJack123)。
* 修复 REPL 语法报告在有效解析时崩溃的问题。
* 使写入 ID 文件成为原子操作。
* 在使用 Fabric API 传输物品时遵守堆叠限制。
* 在 `textutils.serialize` 中忽略元表。
* 在计算 NBT 哈希时正确递归到 NBT 列表 (Lemmmy)。
* 修复高级口袋计算机渲染为灰度的问题。
* 修复使用 `shell` 作为 hashbang 程序时堆栈溢出。
* 修复使用非默认压缩设置时 websocket 消息为空的问题。
* 修复收到重复位置时 `gps.locate` 返回 `nan` 的问题 (Wojbie)。
* 移除 Java 端参数解析代码中的几个线程安全问题。

# CC: Tweaked 1.103.1 新功能

若干错误修复：
* 修复 REPL 中未打印值的问题。
* 修复 REPL 中 `function f()` 提供次优解析错误的问题。

# CC: Tweaked 1.103.0 新功能

* shell 现在支持 hashbang (`#!`) (emmachase)。
* `edit` 中的错误消息现在在高级计算机上以红色显示。
* `turtle.getItemDetail` 现在始终包含 `nbt` 哈希。
* 改进 shell 和 REPL 中错误的显示。
* 海龟、口袋计算机和磁盘可以通过仔细应用（即合成）海绵来褪色。
* 海龟不能再通过右键来染色/褪色。

若干错误修复：
* 若干文档改进和修复 (ouroborus, LelouBil)。
* 修复在向当前计算机发送消息时 rednet 排队错误消息的问题。
* 修复 `__len` 元方法产生时 Lua VM 崩溃的问题。
* `pocket.{un,}equipBack` 现在在卸下升级时正确复制堆栈。
* 修复在按下计算机快捷键时 `key` 事件未被排队的问题。

# CC: Tweaked 1.102.2 新功能

若干错误修复：
* 修复物品框架中打印输出崩溃的问题。
* 修复在磁盘驱动器中放置磁盘时未分配 ID 的问题。

# CC: Tweaked 1.102.1 新功能

若干错误修复：
* 修复在 Fabric 上使用非燃料物品加油时崩溃的问题 (emmachase)。
* 修复使用无线调制解调器调用 `pocket.equipBack()` 时崩溃的问题。
* 修复海龟移动时掉落物品栏的问题 (emmachase)。
* 修复向满库存插入物品时崩溃的问题 (emmachase)。
* 简化有线线缆破坏代码，修复物品有时不掉落的问题。
* 正确处理 Fabric 下将双箱子视为单箱子的问题。
* 修复 Fabric 下未触发 `mouse_up` 的问题。
* 修复放置时全块有线调制解调器未连接到相邻线缆的问题。
* 从 `itemGroups` 物品详情中隐藏搜索标签。
* 修复扬声器播放声音过大。
* 更改海龟掉落物品的位置，减少物品穿过方块的几率。
* 修复 `computer_threads` 配置选项在 Fabric 下未应用的问题。
* 修复日志代码中的堆栈溢出。

# CC: Tweaked 1.102.0 新功能

* `fs.isReadOnly` 现在读取文件系统属性 (Lemmmy)。
* `IComputerAccess.executeMainThreadTask` 不再通过 Lua 往返值。
* 海龟标签现在在移动时动画。

若干错误修复：
* 从文件系统路径中修整空格。
* 正确格式化 `%I` 的 12AM/PM。
* 修复 `import.lua` 上传文件失败的问题。
* 修复高延迟服务器上重复挥动动画的问题 (emmachase)。
* 修复稀疏 Lua 表的几个问题 (Shiranuit)。

# CC: Tweaked 1.101.1 新功能

若干错误修复：
* 改进 rednet 消息的验证 (Ale32bit)。
* 修复 `turtle.refuel()` 总是失败的问题。

# CC: Tweaked 1.101.0 新功能

* 改进荷兰语翻译 (Quezler)
* 更好地报告服务器日志中致命的计算机超时。
* 将详情提供者转换为注册表，允许外设 mod 读取物品/方块详情。
* 重新设计指标系统。`/computercraft track` 现在允许对任何指标计算聚合（总计、最大值、平均值），不仅仅是计算机时间。
* 文件拖放现在在计算机上排队 `file_transfer` 事件。内置 shell 或 `import` 程序现在必须运行才能上传文件。
* `peripheral` 现在使用任何具有 `peripheral_hub` 类型的外设来搜索远程外设，而不仅仅是有线调制解调器。
* 为 `fs.complete` 添加 `include_hidden` 选项，可用于防止隐藏文件出现在自动完成结果中。(IvoLeal72)。
* 添加 `shell.autocomplete_hidden` 设置。(IvoLeal72)

若干错误修复：
* 阻止 `edit` 的"运行"命令在小屏幕上滚动终端输出。
* 移除计算物品 `nbt` 哈希中的一些非确定性。
* 不要在传出 websocket 请求上设置 `Origin` 头。

# CC: Tweaked 1.100.10 新功能

* 在 speaker 帮助中提及 WAV 支持 (MCJack123)。
* 即使在未启用 http 时也将 http 程序添加到路径。

若干错误修复：
* 修复 `textutils.pagedTabulate` 文档中的示例 (IvoLeal72)。
* 修复帮助程序将终端视为比实际长一行的问题。
* 海龟移动时向客户端发送方块更新 (roland-a)。
* 在运行 Occulus 着色器时解决几个监视器问题。

# CC: Tweaked 1.100.9 新功能

* 添加 GPS 设置文档 (Lupus590)。
* 为 `speaker` 程序添加 WAV 支持 (MCJack123)。
* 在 `getItemDetail` 中暴露物品组 (itisluiz)。
* 其他文档修复 (Erb3, JohnnyIrvin)。
* 添加挪威语翻译 (Erb3)。

若干错误修复：
* 修复粗体打印输出边框上的 z-fighting (toad-dev)。
* 修复某些字符串上 `term.blit` 失败的问题。
* 修复 `getItemLimit()` 使用错误槽位的问题 (heap-underflow)。
* 增加监视器深度阻挡器的大小。

# CC: Tweaked 1.100.8 新功能

若干错误修复：
* 修复磁盘驱动器和打印机代码中的 NPE。

# CC: Tweaked 1.100.7 新功能

* 修复在开发环境外启动失败的问题。


# CC: Tweaked 1.100.6 新功能

* 各种文档改进 (MCJack123, FayneAldan)。
* 允许在结构方块中使用时旋转 CC 的方块 (Seniorendi)。
* 计算机执行的若干性能改进。
* 为 `textutils.unserialiseJSON` 添加 `parse_empty_array` 选项 (@ChickChicky)。
* 添加 API 以允许其他 mod 提供额外的物品/方块详情 (Lemmmy)。
* 所有带有 GUI 的方块现在都可以被"锁定"（通过命令或 NBT 编辑工具），就像 vanilla 库存一样。玩家只能用特定名称的物品与其交互。

若干错误修复：
* 修复打印输出在物品框架中渲染偏移的问题 (coolsa)。
* 减少口袋计算机播放音频时的位置延迟。
* 修复 `/computercraft turn-on|shutdown` 命令中的总数问题。
* 修复从子目录运行时编辑器中"运行"命令不工作的问题 (Wojbie)。
* 口袋计算机正确保留其开机状态。

# CC: Tweaked 1.100.5 新功能

* 通用外设现在在给定侧面上使用功能（如果内部侧面上未提供）。
* 提高监视器渲染的性能。

若干错误修复：
* 各种文档修复 (bclindner, Hasaabitt)
* 扬声器声音现在正确地定位在扬声器方块的中心。

# CC: Tweaked 1.100.4 新功能

若干错误修复：
* 修复监视器监视在区块加载缓慢时阻塞主线程的问题。

# CC: Tweaked 1.100.3 新功能

若干错误修复：
* 修复上传大文件时客户端断开连接的问题。
* 正确处理空的计算机 ID 文件。
* 修复普通海龟配方未解锁的问题。
* 移除海龟假实体类型。

# CC: Tweaked 1.100.2 新功能

若干错误修复：
* 修复有线调制解调器交换调制解调器/外设方块状态的问题。
* 从 `turtle.attack` 中移除调试日志行。

# CC: Tweaked 1.100.1 新功能

若干错误修复：
* 修复 `peripheral.hasType` 对有线调制解调器不工作的问题 (Toad-Dev)。
* 修复吵闹的口袋计算机关闭时崩溃的问题。

# CC: Tweaked 1.100.0 新功能

* 扬声器现在可以播放任意 PCM 音频。
* 添加对编码和解码 DFPWM 流的支持，带有 `cc.audio.dfpwm` 模块。
* 有线调制解调器现在只为正在被破坏的部分渲染破坏进度。
* 各种文档改进。

若干错误修复：
* 修复"重复"程序不重复广播 rednet 消息的问题。
* 修复拖放上传功能写入空文件的问题。
* 阻止海龟推动不可推动的实体。

# CC: Tweaked 1.99.1 新功能

* 将 `package.searchpath` 添加到 `cc.require` API。(MCJack123)
* 为 Java API 在某些受限情况下更高效地消费 Lua 表提供更高效的方式。

若干错误修复：
* 修复打开副手口袋计算机 GUI 时按键"粘滞"的问题。
* 正确处理协程管理器恢复带有 `nil` 事件的 Java 代码。
* 阻止计算机按钮从终端窃取焦点。
* 修复监视器格式不正确时的类转换异常。

# CC: Tweaked 1.99.0 新功能

* 副手中的口袋计算机将打开而不显示终端。你可以环顾四周并与世界交互，但你的键盘将被转发到计算机。(Wojbie, MagGen-hub)。
* 外设现在可以有多种类型。`peripheral.getType` 现在返回多个值，`peripheral.hasType` 检查外设是否具有特定类型。
* 添加几个缺失的键到 `keys` 表。(ralphgod3)
* 在文档中添加功能介绍/更改版本信息。(MCJack123)
* 将文件上传限制增加到 512KiB。
* Rednet 现在可以处理大于 65535 的计算机 ID。(Ale32bit)
* 优化 rednet 消息的去重 (MCJack123)
* 使 `term.blit` 颜色不区分大小写。(Ocawesome101)
* 添加新的 `about` 程序以便于版本识别。(MCJack123)
* 优化 `rednet.run` 中的外设调用。(xAnavrins)
* 为 `commands.getBlockInfo` 添加维度参数。
* 添加 `cc.pretty.pretty_print` 辅助函数 (Lupus590)。
* 恢复 JEI 集成。
* 海龟和口袋计算机升级现在可以通过数据包添加和修改。
* 各种翻译更新 (MORIMORI3017, Ale2Bit, mindy15963)

以及若干错误修复：
* 修复 OP 级别为 4 时各种计算机命令失败的问题。
* 各种文档修复。(xXTurnerLP, MCJack123)
* 修复 `textutils.serialize` 不序列化无穷大和 nan 值的问题。(Wojbie)
* 有线调制解调器现在在外设分离时正确清理挂载。
* 修复配方书中海龟和口袋计算机升级配方不正确的问题。
* 修复通过资源包添加的未在游戏中注册的声音时扬声器不播放声音的问题。
* 修复服务器停止后扬声器升级发送数据包的问题。
* 监视器大小调整已重写，希望使其更稳定。
* 外设现在在计算机 tick 时失效，而不是在外设更改时。
* 修复物品框架中的打印输出和口袋计算机以全亮度渲染的问题。
* 所有 mod 方块现在都有有效的工具（镐）。

# CC: Tweaked 1.98.2 新功能

* 添加日文翻译 (MORIMORI0317)
* 将几个配方迁移到数据生成器。

若干错误修复：
* 修复扬声器声音播放的音量问题。
* 修复手持口袋计算机和打印输出时的几个渲染问题。
* 确保在区块加载时有线调制解调器和线缆加入有线网络。
* 修复使用有线网络时堆栈溢出。

# CC: Tweaked 1.98.1 新功能

若干错误修复：
* 修复监视器放置时未正确调整大小的问题。
* 更新俄语翻译 (DrHesperus)。

# CC: Tweaked 1.98.0 新功能
* 为文件上传添加 motd。
* 添加配置选项以限制 HTTP API 使用的总带宽。

以及若干错误修复：
* 修复 `settings.define` 不接受 nil 第二个参数的问题 (SkyTheCodeMaster)。
* 各种文档修复 (Angalexik, emiliskiskis, SkyTheCodeMaster)。
* 修复海龟界面中未显示所选槽位指示器的问题。
* 修复将打印机作为世界生成的一部分放置时崩溃的问题。
* 修复在多人世界中破坏扬声器时崩溃的问题。
* 为 `http.checkURL` 添加缺失的类型检查。
* 阻止 `parallel.*` 在未提供参数时挂起。
* 阻止 rednet 中消息 ID 为 NaN 时出现问题。
* 修复终端宽度更改时帮助程序崩溃的问题。
* 确保放置时监视器格式正确，防止使用 Carry On 或 Quark 时出现图形故障。
* 在 websocket 客户端中接受更多扩展。
* 阻止 `wget` 在给定无效 URL 且无文件名时崩溃。
* 在 `textutils.slowWrite` 中正确换行。

# CC: Tweaked 1.97.0 新功能

* 更新几个翻译 (Anavrins, Jummit, Naheulf)。
* 添加按钮到 `/computercraft dump` 以查看计算机的文件夹。
* 允许在炼药锅中清洗染色的海龟。
* 为 `monitor` 程序添加比例子命令 (MCJack123)。
* 添加选项使 `textutils.serialize` 不写入缩进 (magiczocker10)。
* 允许使用 `==` 比较向量 (fatboychummy)。
* 改进 SSL 失败的 HTTP 错误消息。
* 允许 `craft` 程序制作无限物品 (fatboychummy)。
* 对各种命令队列施加一些限制。
* 为计算机 GUI 添加关机和终止按钮。
* 为几个程序添加程序子补全 (Wojbie)。
* 更新 `help` 程序以接受和（部分）高亮 markdown 文件。
* 移除调试 API 的配置选项。
* 允许为 websocket 设置子协议头。
* 在专属服务器上添加基本的 JMX 监控。
* 添加对 MoreRed Bundled 的支持。
* 允许通过将文件拖放到计算机上来上传文件。

以及若干错误修复：
* 修复在没有宝藏磁盘时使用宝藏磁盘的 NPE。
* 阻止命令计算机在某些游戏规则关闭时丢弃命令输出。
* 修复卸下升级时海龟未更新外设的问题 (Ronan-H)。
* 修复 Lua VM 中致命错误时计算机未关闭的问题。
* 扬声器现在在破坏时正确停止播放，声音跟随吵闹的海龟和口袋计算机。
* 更新 `wget` 以在面对用户错误时更有弹性。
* 修复退出 `paint` 在 shell 中输入"e"的问题。
* 修复彩色口袋计算机使用错误纹理的问题。
* 正确渲染口袋/普通计算机上的透明背景。
* 不要在单人游戏世界上应用 CraftTweaker 操作两次。

# CC: Tweaked 1.96.0 新功能

* 在"列表"程序中使用 lightGrey 显示文件夹。
* 为库存外设添加 `getItemLimit`。
* 将通用外设系统公开给公共 API。
* 添加 cc.expect.range (Lupus590)。
* 允许直接调用 cc.expect (MCJack123)。
* 大量文档改进。

以及若干错误修复：
* 修复 paintutils.drawLine 错误排序坐标的问题 (lilyzeiset)。
* 改进 JEI 对海龟/口袋升级配方的处理。
* 在 cc.pretty 中正确处理稀疏数组。
* 修复海龟放置监视器时崩溃的问题 (baeuric)。
* 修复非常大的资源文件被视为空的问题。
* 允许海龟使用堆肥器。
* 修复染色海龟时的复制漏洞。

# CC: Tweaked 1.95.3 新功能

若干错误修复：
* 正确将稀疏数组序列化为 JSON (livegamer999)
* 修复唱片上 `hasAudio`/`playAudio` 失败的问题。
* 修复 `rs.getBundledInput` 返回输出而不是输入的问题 (SkyTheCodeMaster)
* 通过 edit 运行的程序现在表现得更好一些 (Wojbie)
* 为 websocket 的 headers 添加 User-Agent。

# CC: Tweaked 1.95.2 新功能

* 为 `fs.attributes` 添加 `isReadOnly` (Lupus590)
* 更多程序现在支持小键盘回车 (Wojbie)

若干错误修复：
* 修复某些命令在专属服务器上解析失败的问题。
* 修复所有磁盘配方在 JEI/配方书中显示生产白色磁盘的问题。
* 希望改进某些欧洲键盘上 AltGr 的 edit 行为。
* 阻止文件在其挂载被移除后可用。
* 修复 `id` 程序在非磁盘物品上崩溃的问题 (Wojbie)。
* 在 JEI 中显示时保留海龟/口袋升级的注册顺序。

# CC: Tweaked 1.95.1 新功能

若干错误修复：
* 命令计算机现在再次掉落物品。
* 恢复用染料合成磁盘。
* 修复可损坏物品的 CraftTweaker 集成。
* 在通用外设系统中捕获反射错误，解决与 Botania 的崩溃。

# CC: Tweaked 1.95.0 新功能

* 优化绘画程序的初始渲染。
* 若干文档改进 (Gibbo3771, MCJack123)。
* `fs.combine` 现在接受多个参数。
* 添加设置 (`bios.strict_globals`) 以在意外声明全局变量时报错。(Lupus590)。
* 添加改进的帮助查看器，允许上下滚动 (MCJack123)。
* 添加 `cc.strings` 模块，包含换行文本的工具 (Lupus590)。
* `clear` 程序现在也允许重置调色板 (Luca0208)。

以及若干错误修复：
* 修复通用外设中的内存泄漏。
* 修复海龟在 tick 时被破坏时崩溃的问题。
* `textutils.*tabulate` 现在接受字符串_或_数字。
* 我们现在拒绝_所有_本地 IP，使用神奇的 `$private` 主机。以前 IPv6 环回接口未被阻止。
* 修复在方块尚未同步时渲染监视器崩溃的问题。你需要重新生成配置文件以应用此更改。
* `read` 现在支持小键盘回车 (TheWireLord)
* 正确处理包含转义字符的 URL 的 HTTP 重定向。
* 修复 `os.epoch` 中的整数溢出。
* 允许使用镐子（和其他物品）用于具有 mod 特定 NBT 的海龟升级。
* 修复 JEI 中出现重复的海龟/口袋升级配方的问题。

# CC: Tweaked 1.94.0 新功能

* 添加窗口可见性的 getter (devomaa)
* 通用外设不再处于实验状态，默认启用。
* 使用 term.blit 在 paintutils 中绘制框 (Lemmmy)。

以及若干错误修复：
* 修复海龟开启时海龟未获得进度的问题。
* 使用正确的透明标志启用绘制手持口袋计算机。
* SNBT 解析的几个错误修复。
* 修复几个程序在用法提示中使用原始名称而不是别名的问题 (Lupus590)。

# CC: Tweaked 1.93.1 新功能

* 各种文档改进 (Lemmmy)。
* 在某些旧显卡上修复 TBO 监视器渲染器 (Lemmmy)。

# CC: Tweaked 1.93.0 新功能

* 更新瑞典语翻译 (Granddave)。
* 打印机使用物品标签检查染料。
* HTTP 规则现在可以针对特定端口。
* 不要通过计算机传播相邻的红石信号。

以及若干错误修复：
* 修复海龟与容器交互时的 NPE。

# CC: Tweaked 1.92.0 新功能

* 升级 Cobalt 版本：
  * 添加对 `__pairs` 元方法的支持。
  * string.format 现在使用 `__tostring` 元方法。
* 添加日期特定的 MOTD (MCJack123)。

以及若干错误修复：
* 正确处理 `textutils.unserailizeJSON` 中的制表符。
* 修复海龟剪羊毛时羊不掉落物品的问题。

# CC: Tweaked 1.91.1 新功能

* 修复海龟与实体交互时崩溃的问题。

# CC: Tweaked 1.91.0 新功能

* [通用外设] 向库存方法暴露物品的 NBT 哈希。
* 升级 Cobalt 版本：
  * 优化字符串连接处理。
  * 添加 `string.{pack,unpack,packsize}` (MCJack123)
* 更新到 1.16.2

以及若干错误修复：
* 在 JSON 字符串中转义非 ASCII 字符 (neumond)
* 使 `fs.attributes` 中的字段名更一致 (abby)
* 修复 `textutils.formatTime` 正确处理 12 AM 的问题 (R93950X)
* 修复海龟多次放置桶的问题。

# CC: Tweaked 1.90.3 新功能

* 修复海龟 GUI 中缺少所选槽位指示器的问题。
* 确保我们从世界目录加载/保存计算机数据，而不是全局目录。

# CC: Tweaked 1.90.2 新功能

* 修复在开发环境外未注册通用外设的问题。
* 修复 `turtle.attack()` 失败的问题。
* 正确设置 `/computercraft` 命令输出的样式。

# CC: Tweaked 1.90.1 新功能

* 更新到 Forge 32.0.69

# CC: Tweaked 1.90.0 新功能

* 添加 `cc.image.nft` 模块，用于处理 nft 文件。(JakobDev)
* [实验性] 为任何没有现有外设的瓦片实体提供通用外设。我们目前提供用于处理库存、流体罐和能量存储的方法。这默认禁用，必须在配置中打开。
* 添加配置以控制监视器和终端的大小。
* 添加配置以控制监视器的最大渲染距离。
* 允许使用 `turtle.getItemDetail(slot, true)` 获取物品的"详细"信息。这将包含通用外设提供的相同信息。

以及若干错误修复：
* 添加回允许与命令计算机交互的配置。
* 修复打印机缺少写方法的问题。
* 修复用海龟杀死实体时的复制漏洞。
* 正确提供 Host 头中的端口 (neumond)。
* 修复 `turtle.craft` 缺少参数时失败的问题。
* 修复错误地"监视"未加载的区块时死锁。
* 修复某些错误中泄露文件的完整路径。

# CC: Tweaked 1.89.1 新功能

* 修复渲染不同大小监视器时崩溃的问题。

# CC: Tweaked 1.89.0 新功能

* 压缩监视器数据，显著减少网络流量。
* 允许限制监视器更新使用的带宽。
* 监视器渲染的几个优化 (Lignum)。
* 向 turtle.inspect 和 `turtle.getItemDetail` 暴露方块和物品标签。

以及若干错误修复：
* 修复 settings.load 在定义的设置上失败的问题。
* 修复 `ejectDisk` 外设方法名称的问题。

# CC: Tweaked 1.88.1 新功能

* 修复对具有太多方法的对象报错。

# CC: Tweaked 1.88.0 新功能

* 计算机和海龟在破坏时现在保留其 ID。
* 添加 `peripheral.getName` - 返回包装外设的名称。
* 减少监视器和终端的网络开销。
* 为监视器添加 TBO 后端，性能显著提升。
* Lua REPL 在声明局部变量时发出警告 (lupus590, exerro)
* 添加配置以允许在生存模式中使用命令计算机。
* 添加 `fs.isDriveRoot` - 检查路径是否是驱动器的根。
* `cc.pretty` 现在可以显示函数的参数及其定义位置。Lua REPL 默认显示参数。
* 将 shell 的 `require`/`package` 实现移动到单独的 `cc.require` 模块。
* 将宝藏程序移动到单独的外部数据包中。

以及若干错误修复：
* 修复 io.lines 不接受参数的问题。
* 修复 settings.load 使用未知全局变量的问题 (MCJack123)。
* 阻止计算机扫描外设两次。

# CC: Tweaked 1.87.1 新功能

* 修复方块不在生存模式掉落物品的问题。

# CC: Tweaked 1.87.0 新功能

* 为许多 Lua 函数添加文档。这在线发布于 https://tweaked.cc/。
* 替换 Lua REPL 中的漂亮打印机。它现在支持显示函数和递归表。这个打印机可以在你自己的代码中通过 `cc.pretty` 模块使用。
* 添加 `fs.getCapacity`。作为 `fs.getFreeSpace` 的补充，它返回所提供驱动器的容量。
* 添加 `fs.getAttributes`。这提供文件大小和类型，以及创建和修改时间。
* 更新 Cobalt 版本。这从 Lua 5.2 和 5.3 移植了几个功能：
  - `__len` 元方法现在可以由表使用。
  - 添加 `\z`、十六进制 (`\x00`) 和 unicode (`\u0000`) 字符串转义码。
  - 添加 `utf8` 库。
  - 更接近地镜像 Lua 的尾调用行为。本机函数不再被尾调用，尾调用显示在堆栈跟踪中。
  - `table.unpack` 现在使用 `__len` 和 `__index` 元方法。
  - 解析器错误现在包括发生错误的标记。
* 添加 `textutils.unserializeJSON`。这可用于解码标准 JSON 和字符串化 NBT。
* `settings` API 现在允许"定义"设置。这允许设置指定默认值和描述。
* 在非口袋计算机上启用 motd。
* 允许在 edit 和 paint 中使用鼠标操作菜单 (JakobDev)。
* 添加丹麦语和韩语翻译 (ChristianLW, mindy15963)
* 在监视器程序中触发 `mouse_up` 事件。
* 允许为 `websocket.receive` 指定超时。
* 增加 websocket 消息的最大限制。
* 优化计算机/磁盘文件夹的容量检查。

以及若干错误修复：
* 修复海龟纹理方向不正确的问题 (magiczocker10)。
* 阻止将文件夹复制到自身。
* 在 `shell.setDir` 中规范化文件路径 (JakobDev)
* 修复海龟将含水方块视为水的问题。
* 为海龟的假玩家注册实体渲染器。

# CC: Tweaked 1.86.2 新功能

* 修复 `peripheral.getMethods` 返回空表的问题。
* 更新到 Minecraft 1.15.2。这目前是 alpha 质量，因此缺少功能可能不稳定。

# CC: Tweaked 1.86.1 新功能

* 为 Lua REPL 的退出函数添加帮助消息。
* 添加更多 MOTD 消息。(osmarks)
* GPS 请求现在匿名进行 (osmarks)
* Cobalt VM 的小内存使用改进。

以及若干错误修复：
* 修复使用数字调用 `write` 时报错。
* 添加缺失的 `io.write` 断言。
* 修复 `mouse_scroll` 事件中的坐标错误。

# CC: Tweaked 1.86.0 新功能

* 添加 PATCH 和 TRACE HTTP 方法。(jaredallard)
* 添加更多 MOTD 消息。(JakobDev)
* 允许通过 CraftTweaker 移除和添加海龟升级。

以及若干错误修复：
* 修复与可穿戴背包交互时崩溃的问题。

# CC: Tweaked 1.85.2 新功能

* 修复在使用高级计算机鼠标时崩溃的问题。

# CC: Tweaked 1.85.1 新功能

* 为 `read` 添加基本鼠标支持

以及若干错误修复：
* 修复海龟没有破坏粒子的问题。
* 水下时正确渲染监视器。
* 调整海龟执行动作的位置，纠正某些交互的行为。
* 修复海龟执行某些动作时的几次崩溃。

# CC: Tweaked 1.85.0 新功能

* `window.reposition` 现在允许更改重定向缓冲区。
* 添加 `cc.completion` 和 `cc.shell.completion` 模块。
* `command.exec` 也在游戏暴露时返回受影响对象的数量。

以及若干错误修复：
* 更改海龟挖掘掉落物的处理方式，提高与某些 mod 的兼容性。
* 修复海龟移动后的一些 GUI 同步问题。
* 修复 `os.day`/`os.time` 使用错误的游戏时间的问题。
* 阻止有线调制解调器错误掉落。
* 修复计算机 GUI 中鼠标事件未触发的问题。

# CC: Tweaked 1.84.1 新功能

* 更新到最新 Forge

# CC: Tweaked 1.84.0 新功能

* 改进 rename、copy 和 delete 程序中的验证
* 添加 window.getLine - blit 的逆操作
* turtle.refuel 不再消耗超过所需的燃料
* 添加"cc.expect"模块，用于改进的参数类型检查
* 从所有 mod jar 挂载 ROM，而不仅仅是 CC 的

以及若干错误修复：
* 确保文件错误消息使用绝对正确的路径
* 修复关闭文件多次时出现 NPE。
* 调用 writeDescription 时不加载区块。
* 修复 loadfile 的签名
* 修复海龟多次挖掘方块
* 改进各种外设的线程安全
* 阻止打印页面有巨大/格式错误的标题

# CC: Tweaked 1.83.1 新功能

* 添加几个新的 MOTD 消息 (JakobDev)

以及若干错误修复：
* 修复 `rednet.lookup` 中的类型检查
* 如果海龟和口袋计算机程序在错误的系统上运行则报错 (JakobDev)
* 不要在 nil 后丢弃可变参数。

# CC: Tweaked 1.83.0 新功能

* 添加中文翻译 (XuyuEre)
* 数据包发送的小性能优化。
* 向从 shell 运行的程序提供 `arg` 表，类似于 PUC Lua。
* 添加 `os.date`，并处理向 `os.time` 传递日期时间表，使它们与 PUC Lua 大致兼容。
* `rm` 和 `mkdir` 接受多个参数 (hydraz, JakobDev)。
* 重新设计手持口袋计算机的渲染。
* 阻止在监视器屏幕上渲染边界框。
* 将 Lua 端类型检查代码重构为单一方法。还在错误消息中包含函数名。

以及若干错误修复：
* 修复服务器 tick 预算的计算错误。
* 修复基于列表的配置选项不重新加载的问题。
* 确保 `require` 在 Lua REPL 中可用。

# CC: Tweaked 1.82.3 新功能

* 使计算机的红石输入处理与中继器一致。平行于计算机的红石输入现在将被拾取。

以及若干错误修复：
* 修复 `turtle.compare*()` 导致服务器崩溃的问题。
* 修复协程在 Java 方法上阻塞被丢弃时 Cobalt 泄漏线程的问题。
* 修复 `rawset` 允许 nan 键
* 修复处理格式不正确模式时的几个越界异常。

# CC: Tweaked 1.82.2 新功能

* 不要将 `turtle.refuel`/`refuel` 脚本的限制与物品堆叠大小绑定

以及若干错误修复：
* 修复 Project:Red 输入的更改未被检测到的问题。
* 也将非调制解调器外设转换为多部分，修复 Proportional Destruction Particles 的崩溃
* 移除几个过于急切的错误消息
* 修复有线调制解调器未正确保存其附加外设的问题

# CC: Tweaked 1.82.1 新功能

* 使红石更新与 vanilla 行为相同
* 更新德语翻译

# CC: Tweaked 1.82.0 新功能

* 在 `pastebin put` 可能触发垃圾邮件保护时发出警告 (Lemmmy)
* 在 pastebin 请求上显示 HTTP 错误 (Lemmmy)
* 在主线程上附加外设，而不是延迟到计算机线程。
* 计算机现在可能会在运行时间过长时被抢先中断。这减少了恶意或编写不当的程序使其他计算机无法使用的风险。
* 减少使用更多计算机线程时的开销。
* 在启动计算机时设置初始 multishell 选项卡。这解决了在第一次 yield 之前你不会看到任何内容的问题。
* 允许使用 URL 运行 `pastebin get|url`（例如 `pastebin run https://pastebin.com/LYAxmSby`）。
* 使 `os.time`/`os.day` 不区分大小写。
* 为几种语言添加翻译：巴西葡萄牙语 (zardyh)、瑞典语 (nothjarnan)、意大利语 (Ale32bit)、法语 (absolument)、德语 (Wilma456)、西班牙语 (daelvn)
* 改进海龟/口袋计算机升级的 JEI 集成。你现在可以看到任何升级或升级组合的配方和用途。
* 将海龟/口袋计算机升级与注册它们的 mod 关联。例如，"感知海龟"现在将被标记为属于 Plethora。
* 关闭 GUI 时触发 `key_up` 和 `mouse_up` 事件。
* 允许限制计算机可以消耗的服务器时间量。
* 为海龟加油和物品检查添加几个新事件。未来应该允许附加 mod 有更大的灵活性。
* `rednet.send` 返回消息是否已发送。恢复 CC 1.6 之前存在的行为 (Luca0208)
* 添加 MCMP 集成用于无线和末影调制解调器。
* 使海龟合成与 vanilla 行为更一致。
* `commands.getBlockInfo(s)` 现在也包含 NBT。
* 海龟在用未命名名称牌点击时不再重置其标签。

以及若干错误修复：
* 更新 Cobalt（修复 `load` 不展开堆栈）
* 修复 `commands.collapseArgs` 附加尾随空格的问题。
* 修复关闭时泄露文件描述符（见 [此 JVM bug!] (https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8220477)）
* 修复某些无效 URL 上的 NPE
* 修复口袋计算机 API 在玩家物品栏外工作的问题
* 修复打印未更新输出显示状态的问题。

# CC: Tweaked 1.81.1 新功能

* 修复 colour.*RGB 使用 8 位值，而不是 0-1 浮点数。

# CC: Tweaked 1.81.0 新功能

* 处理 websocket 上的连接错误 (Devilholk)
* 使 `require` 与 PUC Lua 更一致，将所需名称传递给模块并改进错误消息。
* 在分析工具中追踪每个海龟动作花费的时间
* 升级 Cobalt 版本
  * 协程不再由线程支持，减少协程开销。
  * 最大堆栈深度大得多（2^16 而不是 2^8）
  * 发生未处理错误时堆栈不再展开，意味着 `debug.traceback` 可用于死协程。
* 通过减少我们捆绑的额外文件数量来减少 jar 大小。
* 添加 `term.nativePaletteColo(u)r` (Lignum)
* 将 `colours.rgb8` 拆分为 `colours.packRGB` 和 `colours.unpackRGB` (Lignum)
* 打印机现在只接受纸和墨水，而不是任何物品
* 当运行大量程序时，允许滚动 multishell 选项卡栏。(Wilma456)

以及若干错误修复：
* 修复调制解调器应该高级时却不高级的问题
* 修复某些外设方块未设置方向的问题
* 在二进制句柄上从 `.readLine` 剥离 `\r`。
* Websocket 正确处理 ping
* 修复区块卸载时海龟外设不同步的问题。
* `/computercraft` 表格现在正确截断。

# CC: Tweaked 1.80pr1.14 新功能

* 允许在 ROM 文件中查找。
* 修复安装 Astral Sorcery 时无法合成升级海龟或口袋计算机的问题。
* 使几个瓦片实体（调制解调器、线缆和监视器）非 tick，大幅减少开销。

以及若干错误修复：
* 修复线缆不渲染破坏步骤的问题
* 尝试阻止 `/computercraft_copy` 出现在自动补全中。
* 修复 ROM 挂载的几个内存泄漏和其他问题。

# CC: Tweaked 1.80pr1.13 新功能

* `websocket_message` 和 `.receive` 现在返回消息是否为二进制。
* `websocket_close` 事件可能包含状态码和 socket 关闭的原因。
* 默认启用 `debug` 库。
* 清理配置文件，将各种属性移动到子类别中。
* 使用 Netty 重写 HTTP API。
* HTTP 请求现在可以在服务器请求时从 http 重定向到 https。
* 添加配置选项以限制 HTTP API 的各个部分：
  * 限制活动 http 请求和 websocket 的数量。
  * 限制 HTTP 请求和响应的大小。
  * 引入可配置的超时
