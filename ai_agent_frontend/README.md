# AI 应用中心（前端）

Vue 3 + Vite + Vue Router + Axios，对接 Spring Boot AI 接口。

## 功能

- **主页**：切换「AI 恋爱大师」「AI 超级智能体」
- **AI 恋爱大师**：聊天室 UI，进入页自动生成 `chatId`，SSE 调用 `/api/ai/ai_app/chat/sse`
- **AI 超级智能体**：同上 UI，SSE 调用 `/api/ai/manus/chat`

## 启动

```bash
npm install
npm run dev
```

浏览器访问：http://localhost:5173

请确保后端已启动：`http://localhost:8123`

## 接口说明

| 应用 | 方法 | 路径 | 参数 |
|------|------|------|------|
| 恋爱大师 | GET (SSE) | `/api/ai/ai_app/chat/sse` | `message`, `chatId` |
| 超级智能体 | GET (SSE) | `/api/ai/manus/chat` | `message` |

开发环境通过 `vite.config.js` 将 `/api` 代理到 `http://localhost:8123`。

生产环境可在 `.env` 中设置：

```
VITE_API_BASE_URL=http://localhost:8123/api
```

## 构建

```bash
npm run build
npm run preview
```
