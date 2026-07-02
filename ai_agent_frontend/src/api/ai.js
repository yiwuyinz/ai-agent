import axios from 'axios'
import { API_BASE_URL } from './config'

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
})

/**
 * 构建 SSE 请求 URL（GET，query 参数）
 */
export function buildSseUrl(path, params) {
  const base = (
    API_BASE_URL.startsWith('http')
      ? API_BASE_URL
      : `${window.location.origin}${API_BASE_URL}`
  ).replace(/\/$/, '')
  const url = new URL(`${base}${path.startsWith('/') ? path : `/${path}`}`)
  Object.entries(params).forEach(([key, value]) => {
    if (value != null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  })
  return url.toString()
}

/** AI 恋爱大师 - SSE 流式对话 */
export function getAiAppChatSseUrl(message, chatId) {
  return buildSseUrl('/ai/ai_app/chat/sse', { message, chatId })
}

/** AI 超级智能体 - SSE 流式对话 */
export function getManusChatSseUrl(message) {
  return buildSseUrl('/ai/manus/chat', { message })
}

export { http }
