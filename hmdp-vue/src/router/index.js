import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/shops', name: 'shops', component: () => import('../views/ShopListView.vue') },
  { path: '/shops/:id', name: 'shop-detail', component: () => import('../views/ShopDetailView.vue') },
  { path: '/blogs/:id', name: 'blog-detail', component: () => import('../views/BlogDetailView.vue') },
  { path: '/publish', name: 'publish', component: () => import('../views/PublishView.vue'), meta: { auth: true } },
  { path: '/planner', name: 'planner', component: () => import('../views/PlannerView.vue') },
  { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { auth: true } },
  { path: '/users/:id', name: 'user', component: () => import('../views/UserView.vue') },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { bare: true } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })

router.beforeEach(to => {
  if (to.meta.auth && !sessionStorage.getItem('token')) return { name: 'login', query: { redirect: to.fullPath } }
})

export default router
