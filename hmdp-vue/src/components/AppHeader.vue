<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { imageUrl } from '../utils/format'

const router = useRouter()
const user = ref(null)
const keyword = ref('')

onMounted(async () => {
  if (!sessionStorage.getItem('token')) return
  try { user.value = await http.get('/user/me') } catch { user.value = null }
})

const search = () => router.push({ name: 'shops', query: { name: keyword.value } })
</script>

<template>
  <header class="site-header">
    <div class="nav-wrap">
      <router-link class="brand" to="/"><span class="brand-mark">C</span><span>CityHub</span></router-link>
      <nav class="main-nav">
        <router-link to="/">发现</router-link>
        <router-link to="/shops">商家</router-link>
        <router-link to="/planner">智能规划</router-link>
        <router-link to="/publish">发布笔记</router-link>
      </nav>
      <form class="nav-search" @submit.prevent="search">
        <input v-model="keyword" placeholder="搜索商家与城市好去处" />
        <button>搜索</button>
      </form>
      <router-link v-if="user" class="user-entry" to="/profile">
        <img :src="imageUrl(user.icon)" /><span>{{ user.nickName }}</span>
      </router-link>
      <router-link v-else class="login-entry" to="/login">登录</router-link>
    </div>
  </header>
</template>
