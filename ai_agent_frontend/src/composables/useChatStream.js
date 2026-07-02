import { ref } from 'vue'
import { streamSse } from '../utils/sse'

let messageId = 0

function nextId() {
  messageId += 1
  return `msg-${messageId}-${Date.now()}`
}

export function useChatStream(buildUrl) {
  const messages = ref([])
  const loading = ref(false)
  let abortController = null

  function appendUserMessage(content) {
    messages.value.push({
      id: nextId(),
      role: 'user',
      content,
      streaming: false,
    })
  }

  function appendAiMessage() {
    const aiMsg = {
      id: nextId(),
      role: 'ai',
      content: '',
      streaming: true,
    }
    messages.value.push(aiMsg)
    return aiMsg
  }

  async function send(userText, extraParams = {}) {
    const text = userText.trim()
    if (!text || loading.value) return

    appendUserMessage(text)
    appendAiMessage()
    const aiIndex = messages.value.length - 1
    loading.value = true
    abortController = new AbortController()

    try {
      const url = buildUrl(text, extraParams)
      await streamSse(
        url,
        (chunk) => {
          messages.value[aiIndex].content += chunk
        },
        abortController.signal,
      )
    } catch (err) {
      if (err.name === 'AbortError') return
      const current = messages.value[aiIndex].content
      messages.value[aiIndex].content =
        current || `出错了：${err.message || '请检查后端服务是否已启动'}`
    } finally {
      messages.value[aiIndex].streaming = false
      loading.value = false
      abortController = null
    }
  }

  function stop() {
    abortController?.abort()
  }

  return { messages, loading, send, stop }
}
