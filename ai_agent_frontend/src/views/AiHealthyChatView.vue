<template>
  <ChatRoom
    title="AI 健康助手"
    :session-label="`会话 ID：${chatId}`"
    :messages="messages"
    :loading="loading"
    @send="onSend"
  />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import ChatRoom from '../components/ChatRoom.vue'
import { getAiAppChatSseUrl } from '../api/ai.js'
import { createChatId } from '../utils/chatId.js'
import { useChatStream } from '../composables/useChatStream.js'

const chatId = ref('')

onMounted(() => {
  chatId.value = createChatId()
})

const { messages, loading, send } = useChatStream((message) =>
  getAiAppChatSseUrl(message, chatId.value),
)

function onSend(text) {
  if (!chatId.value) {
    chatId.value = createChatId()
  }
  send(text)
}
</script>
