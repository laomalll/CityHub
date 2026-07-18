import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/main.css'
import './styles/pagination.css'
import './styles/profile.css'
import './styles/article-comments.css'
import './styles/planner.css'
import './styles/shop-detail.css'

createApp(App).use(router).mount('#app')
