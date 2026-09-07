speaker 程序使用连接到计算机的扬声器播放音频文件。

它支持有限数量的音频格式：
* DFPWM：你可以使用外部工具（如 https://music.madefor.cc）将音乐转换为 DFPWM 格式。
* WAV：WAV 文件必须是 8 位 PCM 或 DFPWM 格式，仅限单声道，采样率为 48kHz。

## 示例：
* `speaker play example.dfpwm left` 使用计算机左侧的扬声器播放 "example.dfpwm" 音频文件。
* `speaker stop` 停止当前正在播放的音频。
