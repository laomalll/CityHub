<script setup>
import { ref, onMounted } from 'vue'
import http from '../api/http'
import BlogCard from '../components/BlogCard.vue'

const types = ref([])
const blogs = ref([])
const loading = ref(true)
const currentPage = ref(1)
const totalPages = ref(0)
const pageSize = 9

const categoryIcon = icon => {
  if (!icon) return '/imgs/icons/default-icon.png'
  return icon.startsWith('/imgs/') ? icon : `/imgs${icon.startsWith('/') ? icon : `/${icon}`}`
}

const loadBlogs = async (page = 1) => {
  loading.value = true
  try {
    const result = await http.get('/blog/hot', { params: { current: page, pageSize } })
    blogs.value = result.records || []
    currentPage.value = Number(result.current || page)
    totalPages.value = Number(result.pages || 0)
  } finally {
    loading.value = false
  }
}

const changePage = async page => {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  await loadBlogs(page)
  document.querySelector('.story-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(async () => {
  types.value = await http.get('/shop-type/list')
  await loadBlogs(1)
})
</script>

<template>
  <div>
    <section class="hero container">
      <div class="hero-copy">
        <span class="eyebrow">YOUR CITY, YOUR STORY</span>
        <h1>发现城市里<br /><em>值得分享</em>的日常</h1>
        <p>从一杯咖啡到一次周末出逃，探索真实评价、附近好店与限时优惠。</p>
        <div class="hero-actions"><router-link class="primary-btn" to="/shops">探索附近商家</router-link><router-link class="text-btn" to="/publish">分享新发现 →</router-link></div>
      </div>
      <div class="hero-art"><div class="art-circle"></div><div class="art-card art-one">今日灵感<br /><strong>城市漫游</strong></div><div class="art-card art-two">精选好店<br /><strong>4.9 ★</strong></div></div>
    </section>

    <section class="container section-block">
      <div class="section-head"><div><span class="eyebrow">EXPLORE</span><h2>从兴趣开始探索</h2></div><router-link to="/shops">查看全部 →</router-link></div>
      <div class="category-grid">
        <router-link v-for="type in types" :key="type.id" :to="{ name: 'shops', query: { type: type.id, name: type.name } }" class="category-card">
          <img :src="categoryIcon(type.icon)" /><span>{{ type.name }}</span>
        </router-link>
      </div>
    </section>

    <section class="story-section">
      <div class="container section-block">
        <div class="section-head"><div><span class="eyebrow">CITY STORIES</span><h2>大家正在分享</h2></div></div>
        <div v-if="loading" class="empty-state">正在加载城市故事…</div>
        <template v-else>
          <div v-if="blogs.length" class="blog-grid"><BlogCard v-for="blog in blogs" :key="blog.id" :blog="blog" /></div>
          <div v-else class="empty-state">暂时还没有用户发布探店笔记。</div>
          <nav v-if="totalPages > 1" class="pagination" aria-label="探店笔记分页">
            <button :disabled="currentPage === 1" @click="changePage(currentPage - 1)">上一页</button>
            <button v-for="page in totalPages" :key="page" :class="{ active: page === currentPage }" :aria-current="page === currentPage ? 'page' : undefined" @click="changePage(page)">{{ page }}</button>
            <button :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">下一页</button>
          </nav>
        </template>
      </div>
    </section>
  </div>
</template>
