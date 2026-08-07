# 公益 API OpenAI 兼容代理 APK

这是一个精简 Android APK，把 `https://free-api.cnmwx.com/v1/completions` 包装成本机 OpenAI 兼容接口。它会在手机上启动一个前台服务，默认监听 `8787` 端口，并自动隐藏上游返回中的广告行。

## 功能

- 提供 `POST /v1/chat/completions`
- 提供 `POST /v1/completions`
- 提供 `GET /v1/models`
- 支持 OpenAI 风格流式 SSE：`data: {...}\n\n` 与 `[DONE]`
- 支持非流式 JSON 响应
- 无需 API Key
- Android 前台服务运行，通知栏可看到运行状态
- 简洁原生 UI，可一键启动、停止、复制接口地址

## 使用

1. 安装 Release 中的 APK。
2. 打开应用，点击“启动代理”。
3. 在同一台手机上使用：

```text
http://127.0.0.1:8787/v1/chat/completions
```

如果需要让局域网内其他设备访问，请确保手机和客户端在同一网络，并使用手机局域网 IP，例如：

```text
http://手机IP:8787/v1/chat/completions
```

## 请求示例

```bash
curl http://127.0.0.1:8787/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "free-api",
    "stream": true,
    "messages": [
      {"role": "user", "content": "你好"}
    ]
  }'
```

## 说明

上游公益接口要求请求体格式为：

```json
{"prompt":"你的问题"}
```

本项目会把 OpenAI `messages` 或 `prompt` 转换为上游 `prompt`，再把上游 SSE 或纯文本响应转换回 OpenAI 兼容格式。
