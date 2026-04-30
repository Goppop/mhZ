import { createRouter, createWebHistory } from 'vue-router'
import HtmlConfigWorkbench from '@/views/HtmlConfigWorkbench.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/html-config',
      name: 'HtmlConfig',
      component: HtmlConfigWorkbench,
    },
    {
      path: '/',
      redirect: '/html-config',
    },
  ],
})

export default router