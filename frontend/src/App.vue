<script setup>
import { ref } from 'vue'
import OrderTable from './components/OrderTable.vue'
import ChatPanel from './components/ChatPanel.vue'

// 每次对话有新回复后 +1，驱动订单表自动刷新（退订后状态变化可见）
const reloadKey = ref(0)
</script>

<template>
  <div class="layout">
    <header class="header">
      <h1>航空智能客服</h1>
      <span class="subtitle">DeepSeek · 对话记忆 · 订单 Tools</span>
    </header>

    <main class="main">
      <section class="panel orders">
        <h2>订单列表</h2>
        <OrderTable :reload-key="reloadKey" />
      </section>

      <section class="panel chat">
        <h2>智能客服对话</h2>
        <ChatPanel @replied="reloadKey++" />
      </section>
    </main>
  </div>
</template>

<style scoped>
.layout {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
}

.header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 12px 4px 20px;
}

.header h1 {
  font-size: 22px;
  color: #1d4ed8;
}

.subtitle {
  font-size: 13px;
  color: #6b7280;
}

.main {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 900px) {
  .main {
    grid-template-columns: 1fr;
  }
}

.panel {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.08);
}

.panel h2 {
  font-size: 15px;
  margin-bottom: 12px;
  color: #374151;
}
</style>
