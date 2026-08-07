# 公益 API OpenAI 兼容代理 APK

将 `https://free-api.cnmwx.com/v1/completions` 公益 API 包装为本地 OpenAI 兼容接口的 Android 应用。在手机上启动前台服务，监听 `8787` 端口，自动过滤广告，支持工具调用、流式响应和多模型别名。

## 功能特性

- **OpenAI 兼容接口**：`/v1/chat/completions`、`/v1/completions`、`/v1/models`
- **API Key 支持**：接受任意 Bearer Token（兼容所有 OpenAI 客户端）
- **模型列表**：支持 `free-api`、`gemini-pro`、`gemini-1.5-pro`、`gpt-4o` 等多个别名
- **工具调用**：通过提示词注入 + `<tool_call>` 标签解析实现 function calling
- **流式 SSE**：标准 `data: {...}\n\n` 格式，支持 `stream: true`
- **广告过滤**：自动移除上游响应中的广告内容
- **深色模式 UI**：Material Design 深色主题，卡片式布局
- **前台服务**：后台持久运行，通知栏显示状态
- **内置测试**：一键测试连接，查看模型回复

## 快速开始

1. 从 [Releases](../../releases) 下载最新 APK
2. 安装后打开应用，点击「启动代理」
3. 在 OpenAI 客户端中配置：

| 配置项 | 值 |
|--------|-----|
| Base URL | `http://127.0.0.1:8787/v1` |
| API Key | `sk-free-api`（任意值均可） |
| Model | `free-api`（或其他别名） |

## 请求示例

### 非流式

```bash
curl http://127.0.0.1:8787/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-free-api" \
  -d '{
    "model": "free-api",
    "messages": [{"role": "user", "content": "你好"}]
  }'
```

### 流式

```bash
curl http://127.0.0.1:8787/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "free-api",
    "stream": true,
    "messages": [{"role": "user", "content": "写一首诗"}]
  }'
```

### 工具调用

```bash
curl http://127.0.0.1:8787/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "free-api",
    "messages": [{"role": "user", "content": "北京今天天气怎么样？"}],
    "tools": [{
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "获取指定城市的天气",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {"type": "string", "description": "城市名称"}
          },
          "required": ["city"]
        }
      }
    }]
  }'
```

### Python SDK

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://127.0.0.1:8787/v1",
    api_key="sk-free-api"
)

response = client.chat.completions.create(
    model="free-api",
    messages=[{"role": "user", "content": "你好"}]
)
print(response.choices[0].message.content)
```

## 局域网访问

如需让其他设备访问，将 `127.0.0.1` 替换为手机的局域网 IP：

```text
http://手机IP:8787/v1/chat/completions
```

## 可用模型

| 模型名 | 说明 |
|--------|------|
| `free-api` | 默认模型 |
| `gemini-pro` | Gemini Pro 别名 |
| `gemini-1.5-pro` | Gemini 1.5 Pro 别名 |
| `gemini-1.5-flash` | Gemini 1.5 Flash 别名 |
| `gpt-4o` | 兼容性别名 |
| `gpt-4o-mini` | 兼容性别名 |
| `deepseek-chat` | 兼容性别名 |

所有模型均映射到同一上游 API（Google Gemini 后端）。

## 技术架构

- **NanoHTTPD**：本地 HTTP 服务器
- **OkHttp**：上游 API 请求
- **提示词注入**：工具定义注入到 system 消息
- **标签解析**：`<tool_call>` 标签解析为 OpenAI tool_calls 格式
- **广告过滤**：正则匹配移除上游广告内容

## 构建

```bash
gradle assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`
