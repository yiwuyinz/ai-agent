import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import AiHealthyChatView from '../views/AiHealthyChatView.vue'
import ManusChatView from '../views/ManusChatView.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
    meta: { title: '应用中心' },
  },
  {
    path: '/ai-healthy',
    name: 'ai-healthy',
    component: AiHealthyChatView,
    meta: { title: 'AI 健康助手' },
  },
  {
    path: '/manus',
    name: 'manus',
    component: ManusChatView,
    meta: { title: 'AI 超级智能体' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} - AI 应用中心`
    : 'AI 应用中心'
})

export default router
