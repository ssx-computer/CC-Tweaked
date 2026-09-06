扬声器程序使用连接到计算机的扬声器来播放音频文件。

支持的音频文件格式有限：
* DFPWM：你可以使用外部工具（如 https://music.madefor.cc）将音乐转换为 DFPWM 格式。
* WAV：WAV 文件必须是 8 位 PCM 或 DFPWM 格式，且必须只有单声道，采样率为 48kHz。

## 示例：
* `speaker play example.dfpwm left` 使用计算机左侧的扬声器播放 "example.dfpwm" 音频文件。
* `speaker stop` 停止当前正在播放的音频。
