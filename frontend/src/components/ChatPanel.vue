<script setup>
import { ref, nextTick } from 'vue'

const emit = defineEmits(['replied'])

const messages = ref([
  {
    role: 'assistant',
    text: '您好，我是航空智能客服。您可以：\n· 查询订单：如"查询FL001"\n· 办理退订：如"我要退订订单FL001"（我会先展示订单信息，待您确认后才会退订）'
  }
])

// 会话 ID：后端生成/回传，续用同一 ID 以延续对话记忆（JDBC 持久化）
const conversationId = ref('')
const input = ref('')
const loading = ref(false)
const listRef = ref(null)

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', text })
  input.value = ''
  loading.value = true

  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text, conversationId: conversationId.value || null })
    })
    const data = await res.json()
    conversationId.value = data.conversationId
    messages.value.push({ role: 'assistant', text: data.reply })
    // 通知父组件：可能发生了退订，刷新订单表
    emit('replied')
  } catch (e) {
    messages.value.push({ role: 'assistant', text: '请求失败：' + e.message })
  } finally {
    loading.value = false
    await nextTick()
    listRef.value?.scrollTo({ top: listRef.value.scrollHeight, behavior: 'smooth' })
  }
}
</script>

<template>
  <div class="chat-panel">
    <div ref="listRef" class="messages">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="message"
        :class="msg.role"
      >
        <div class="bubble">{{ msg.text }}</div>
      </div>
      <div v-if="loading" class="message assistant">
        <div class="bubble typing">客服思考中…</div>
      </div>
    </div>

    <form class="input-bar" @submit.prevent="send">
      <input
        v-model="input"
        type="text"
        placeholder="输入消息，例如：我要退订订单FL001"
        :disabled="loading"
      />
      <button type="submit" :disabled="loading || !input.trim()">发送</button>
    </form>
  </div>
</template>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 480px;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 4px 2px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message {
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 85%;
  padding: 9px 12px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.message.assistant .bubble {
  background: #f3f4f6;
}

.message.user .bubble {
  background: #1d4ed8;
  color: #fff;
}

.typing {
  color: #6b7280;
  font-style: italic;
}

.input-bar {
  display: flex;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid #e5e7eb;
}

.input-bar input {
  flex: 1;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 14px;
  outline: none;
}

.input-bar input:focus {
  border-color: #1d4ed8;
}

.input-bar button {
  border: none;
  background: #1d4ed8;
  color: #fff;
  border-radius: 8px;
  padding: 0 20px;
  font-size: 14px;
  cursor: pointer;
}

.input-bar button:disabled {
  background: #93c5fd;
  cursor: not-allowed;
}
</style>
