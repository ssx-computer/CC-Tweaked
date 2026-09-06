# CC: Tweaked 新功能

## CC: Tweaked 1.120.2 的新功能

几个错误修复：
* 修复如果 `read()` 不产生则 shell 崩溃的问题。
* 修复袖珍/打印讲道坛在屏幕最底部时不渲染的问题。
* 几个文档修复。(tomodachi94)

## CC: Tweaked 1.120.0 的新功能

* 支持在 `parallel.waitForAll` 中生成新的并行函数。

一个错误修复：
* 使 HTTP IP 过滤更严格。

## CC: Tweaked 1.119.0 的新功能

* 添加 `commands.getDimension()`。
* 添加 `cc.base64` 模块。
* 更新 Cobalt 到 0.9.9，带来了多个 Lua 5.5 更改：
  * 浮点数现在打印时带有足够的位数以便正确往返。
  * 添加 `table.create`。
  * `utf8.offset` 现在返回代码点的最终位置。

几个错误修复：
* 修复 `LuaTable` 中整数索引的处理。
* 修正 `os.time` 中 `min` 和 `sec` 的默认值。(sircfenner)
* 使 HTTP IP 过滤更严格。

输入 "help changelog" 查看完整的版本历史。
