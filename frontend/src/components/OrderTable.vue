<script setup>
import { ref, watch, onMounted } from 'vue'

// reloadKey 变化（对话产生新回复后由父组件递增）时自动刷新，退订状态变化立即可见
const props = defineProps({ reloadKey: { type: Number, default: 0 } })

const orders = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await fetch('/api/orders')
    orders.value = await res.json()
  } catch (e) {
    console.error('加载订单失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.reloadKey, load)
</script>

<template>
  <div>
    <div class="toolbar">
      <button class="btn" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
      <span class="hint">通过右侧对话办理退订后，状态会自动刷新</span>
    </div>

    <table class="order-table">
      <thead>
        <tr>
          <th>订单编码</th>
          <th>姓名</th>
          <th>出发地</th>
          <th>目的地</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="order in orders" :key="order.orderNo">
          <td>{{ order.orderNo }}</td>
          <td>{{ order.name }}</td>
          <td>{{ order.departure }}</td>
          <td>{{ order.destination }}</td>
          <td>
            <span class="badge" :class="order.status === '正常' ? 'normal' : 'cancelled'">
              {{ order.status }}
            </span>
          </td>
        </tr>
        <tr v-if="orders.length === 0">
          <td colspan="5" class="empty">暂无订单数据</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.btn {
  border: 1px solid #d1d5db;
  background: #fff;
  border-radius: 6px;
  padding: 4px 12px;
  cursor: pointer;
  font-size: 13px;
}

.btn:hover:not(:disabled) {
  border-color: #1d4ed8;
  color: #1d4ed8;
}

.hint {
  font-size: 12px;
  color: #9ca3af;
}

.order-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.order-table th,
.order-table td {
  border-bottom: 1px solid #e5e7eb;
  padding: 9px 8px;
  text-align: left;
}

.order-table th {
  background: #f9fafb;
  color: #6b7280;
  font-weight: 500;
}

.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.badge.normal {
  background: #dcfce7;
  color: #15803d;
}

.badge.cancelled {
  background: #fee2e2;
  color: #b91c1c;
}

.empty {
  text-align: center;
  color: #9ca3af;
}
</style>
