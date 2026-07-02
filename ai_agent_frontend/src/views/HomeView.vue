<template>
  <div class="home">
    <header class="home-header">
      <h1>AI 应用中心</h1>
      <p>选择要使用的 AI 应用</p>
    </header>

    <section class="app-grid">
      <router-link
        v-for="app in apps"
        :key="app.path"
        :to="app.path"
        class="app-card"
      >
        <span class="app-icon">{{ app.icon }}</span>
        <h2>{{ app.name }}</h2>
        <p>{{ app.description }}</p>
        <span class="app-arrow">进入 →</span>
      </router-link>
    </section>
  </div>
</template>

<script setup>
const apps = [
  {
    path: '/ai-healthy',
    name: 'AI 健康助手',
    icon: '⛑',
    description: '咨询健康问题及建议，聊天室实时对话',
  },
  {
    path: '/manus',
    name: 'AI 智能体',
    icon: '🤖',
    description: '多功能智能体，工具调用与流式回复',
  },
]
</script>

<style scoped>
.home {
  min-height: 100vh;
  max-width: 960px;
  margin: 0 auto;
  padding: 48px 24px;
}

.home-header {
  text-align: center;
  margin-bottom: 48px;
}

.home-header h1 {
  margin: 0 0 8px;
  font-size: 32px;
  font-weight: 700;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.home-header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 16px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
}

.app-card {
  display: flex;
  flex-direction: column;
  padding: 28px;
  border-radius: 16px;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  text-decoration: none;
  color: inherit;
  transition:
    transform 0.2s,
    box-shadow 0.2s,
    border-color 0.2s;
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(99, 102, 241, 0.15);
  border-color: var(--accent);
}

.app-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.app-card h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.app-card p {
  margin: 0;
  flex: 1;
  font-size: 14px;
  color: var(--text-muted);
  line-height: 1.5;
}

.app-arrow {
  margin-top: 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--accent);
}
</style>
