/**
 * 使用 fetch 读取 SSE 流（支持 Spring Flux / SseEmitter）
 * @param {string} url
 * @param {(chunk: string) => void} onMessage
 * @param {AbortSignal} [signal]
 */
export async function streamSse(url, onMessage, signal) {
  const response = await fetch(url, {
    method: 'GET',
    headers: { Accept: 'text/event-stream' },
    signal,
  })

  if (!response.ok) {
    throw new Error(`请求失败: ${response.status} ${response.statusText}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('浏览器不支持流式读取')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith(':')) continue

      if (trimmed.startsWith('data:')) {
        const data = trimmed.slice(5).trimStart()
        if (data && data !== '[DONE]') {
          onMessage(parseSseData(data))
        }
      } else if (!trimmed.startsWith('event:') && !trimmed.startsWith('id:')) {
        onMessage(parseSseData(trimmed))
      }
    }
  }

  if (buffer.trim()) {
    const trimmed = buffer.trim()
    if (trimmed.startsWith('data:')) {
      onMessage(parseSseData(trimmed.slice(5).trimStart()))
    } else if (trimmed) {
      onMessage(parseSseData(trimmed))
    }
  }
}

function parseSseData(raw) {
  if (raw === '[DONE]') return ''
  try {
    const parsed = JSON.parse(raw)
    if (typeof parsed === 'string') return parsed
    if (parsed?.content != null) return String(parsed.content)
    if (parsed?.data != null) return String(parsed.data)
    if (parsed?.text != null) return String(parsed.text)
    return raw
  } catch {
    return raw
  }
}
