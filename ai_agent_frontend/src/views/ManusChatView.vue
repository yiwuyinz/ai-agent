<template>
  <ChatRoom
    title="AI 超级智能体"
    :messages="messages"
    :loading="loading"
    @send="onSend"
  />
</template>

<script setup>
import ChatRoom from '../components/ChatRoom.vue'
import { getManusChatSseUrl } from '../api/ai'
import { useChatStream } from '../composables/useChatStream'

const { messages, loading, send } = useChatStream((message) =>
  getManusChatSseUrl(message),
)

function onSend(text) {
  send(text)
}
</script>
