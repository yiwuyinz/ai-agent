<template>
  <div class="chat-room">
    <header class="chat-header">
      <button type="button" class="back-btn" @click="goHome">← 返回</button>
      <div class="header-info">
        <h1>{{ title }}</h1>
        <p v-if="sessionLabel" class="session-id">{{ sessionLabel }}</p>
      </div>
    </header>

    <main ref="listRef" class="chat-messages">
      <div v-if="messages.length === 0" class="empty-hint">
        发送一条消息开始对话吧
      </div>
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="message-row"
        :class="msg.role === 'user' ? 'is-user' : 'is-ai'"
      >
        <div class="avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
        <div class="bubble">
          <p class="bubble-text">{{ msg.content }}</p>
          <span v-if="msg.streaming" class="typing-cursor">▋</span>
        </div>
      </div>
    </main>

    <footer class="chat-input-bar">
      <textarea
        v-model="inputText"
        class="chat-input"
        rows="2"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        :disabled="loading"
        @keydown.enter.exact.prevent="send"
      />
      <button
        type="button"
        class="send-btn"
        :disabled="loading || !inputText.trim()"
        @click="send"
      >
        {{ loading ? '生成中…' : '发送' }}
      </button>
    </footer>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  title: { type: String, required: true },
  sessionLabel: { type: String, default: '' },
  messages: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['send'])

const router = useRouter()
const inputText = ref('')
const listRef = ref(null)

function goHome() {
  router.push('/')
}

function send() {
  const text = inputText.value.trim()
  if (!text || props.loading) return
  emit('send', text)
  inputText.value = ''
}

watch(
  () => [props.messages.length, props.messages.at(-1)?.content],
  async () => {
    await nextTick()
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  },
  { deep: true },
)
</script>

<style scoped>
.chat-room {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 900px;
  margin: 0 auto;
  background: var(--bg-chat);
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-panel);
}

.back-btn {
  flex-shrink: 0;
  padding: 6px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 14px;
}

.back-btn:hover {
  background: var(--bg-hover);
  color: var(--text);
}

.header-info h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.session-id {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-muted);
  word-break: break-all;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 16px;
}

.empty-hint {
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
  margin-top: 40px;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-start;
}

.message-row.is-user {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.is-ai .avatar {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
}

.is-user .avatar {
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  color: #fff;
}

.bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 14px;
  line-height: 1.6;
  font-size: 15px;
  white-space: pre-wrap;
  word-break: break-word;
}

.is-ai .bubble {
  background: var(--bubble-ai);
  border-top-left-radius: 4px;
  color: var(--text);
}

.is-user .bubble {
  background: var(--bubble-user);
  border-top-right-radius: 4px;
  color: #fff;
}

.bubble-text {
  margin: 0;
  display: inline;
}

.typing-cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  margin-left: 2px;
  color: var(--accent);
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.chat-input-bar {
  display: flex;
  gap: 10px;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--border);
  background: var(--bg-panel);
}

.chat-input {
  flex: 1;
  resize: none;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  font-size: 15px;
  font-family: inherit;
  background: var(--bg-input);
  color: var(--text);
  outline: none;
}

.chat-input:focus {
  border-color: var(--accent);
}

.chat-input:disabled {
  opacity: 0.6;
}

.send-btn {
  align-self: flex-end;
  padding: 10px 20px;
  border: none;
  border-radius: 12px;
  background: var(--accent);
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn:not(:disabled):hover {
  filter: brightness(1.05);
}
</style>
