<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { formatDate, imageUrl } from '../utils/format'

const router = useRouter()
const user = ref({})
const info = ref({})
const blogs = ref([])
const loading = ref(true)
const errorMessage = ref('')
const signing = ref(false)
const signCount = ref(0)
const signMessage = ref('')
const activeTab = ref('notes')
const orders = ref([])
const ordersLoaded = ref(false)
const ordersLoading = ref(false)
const tabMessage = ref('')
const notesPage = ref(1)
const notesPages = ref(0)
const ordersPage = ref(1)
const ordersPages = ref(0)
const pageSize = 6

const orderStatus = status => ({
  1: '未支付', 2: '已支付', 3: '已核销', 4: '已取消', 5: '退款中', 6: '已退款'
}[status] || '处理中')

const loadNotes = async (page = 1) => {
  const result = await http.get('/blog/of/me', { params: { current: page, pageSize } })
  blogs.value = result.records || []
  notesPage.value = Number(result.current || page)
  notesPages.value = Number(result.pages || 0)
}

const loadOrders = async (page = 1) => {
  ordersLoading.value = true
  tabMessage.value = ''
  try {
    const result = await http.get('/voucher-order/of/me', { params: { current: page, pageSize } })
    orders.value = result.records || []
    ordersPage.value = Number(result.current || page)
    ordersPages.value = Number(result.pages || 0)
    ordersLoaded.value = true
  } catch (error) {
    tabMessage.value = error.message
  } finally {
    ordersLoading.value = false
  }
}

const changeNotesPage = async page => {
  if (page < 1 || page > notesPages.value || page === notesPage.value) return
  await loadNotes(page)
  document.querySelector('.profile-tabs')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const changeOrdersPage = async page => {
  if (page < 1 || page > ordersPages.value || page === ordersPage.value) return
  await loadOrders(page)
  document.querySelector('.profile-tabs')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const switchTab = async tab => {
  activeTab.value = tab
  if (tab !== 'orders' || ordersLoaded.value) return
  await loadOrders(1)
}

const loadProfile = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const currentUser = await http.get('/user/me')
    if (!currentUser?.id) {
      sessionStorage.removeItem('token')
      return router.replace({ name: 'login', query: { redirect: '/profile' } })
    }
    user.value = currentUser

    const [userInfo, myBlogs, count] = await Promise.all([
      http.get(`/user/info/${currentUser.id}`),
      http.get('/blog/of/me', { params: { current: 1, pageSize } }),
      http.get('/user/sign/count').catch(() => 0)
    ])
    // 新注册用户可能尚无 tb_user_info 记录，必须兼容后端返回 null。
    info.value = userInfo || {}
    blogs.value = myBlogs.records || []
    notesPage.value = Number(myBlogs.current || 1)
    notesPages.value = Number(myBlogs.pages || 0)
    signCount.value = Number(count || 0)
  } catch (error) {
    errorMessage.value = error.message || '个人资料加载失败'
  } finally {
    loading.value = false
  }
}

const sign = async () => {
  signing.value = true
  signMessage.value = ''
  try {
    await http.post('/user/sign')
    signCount.value = await http.get('/user/sign/count') || 0
    signMessage.value = '签到成功'
  } catch (error) {
    signMessage.value = error.message
  } finally {
    signing.value = false
  }
}

const logout = async () => {
  try {
    await http.post('/user/logout')
  } catch {
    // 当前后端 logout 尚未实现，仍清理浏览器 token 完成本地退出。
  } finally {
    sessionStorage.removeItem('token')
    await router.push('/')
    location.reload()
  }
}

onMounted(loadProfile)
</script>

<template>
  <div class="container profile-page">
    <div v-if="loading" class="empty-state">正在加载个人资料…</div>
    <div v-else-if="errorMessage" class="empty-state">
      <p>{{ errorMessage }}</p>
      <button class="primary-btn" @click="loadProfile">重新加载</button>
    </div>
    <template v-else>
      <section class="profile-banner">
        <div class="profile-main">
          <img :src="imageUrl(user.icon)" alt="用户头像" />
          <div>
            <h1>{{ user.nickName || 'CityHub 用户' }}</h1>
            <p>{{ info.introduce || '这个人很有品位，只是还没写简介。' }}</p>
            <span>CityHub ID · {{ user.id }}</span>
          </div>
        </div>
        <div class="profile-actions">
          <button class="sign-btn" :disabled="signing" @click="sign">
            {{ signing ? '签到中…' : `签到 · 连续 ${signCount} 天` }}
          </button>
          <router-link class="primary-btn" to="/publish">发布新笔记</router-link>
          <button class="ghost-btn" @click="logout">退出登录</button>
        </div>
      </section>
      <p v-if="signMessage" class="profile-notice">{{ signMessage }}</p>
      <section class="profile-content">
        <nav class="profile-tabs" aria-label="个人中心内容导航">
          <button :class="{ active: activeTab === 'notes' }" @click="switchTab('notes')">笔记</button>
          <button :class="{ active: activeTab === 'orders' }" @click="switchTab('orders')">优惠券订单</button>
        </nav>

        <div v-if="activeTab === 'notes'" class="profile-tab-panel">
          <div v-if="!blogs.length" class="empty-state">还没有发布笔记，去记录第一次城市发现吧。</div>
          <div v-else class="profile-note-list">
            <article v-for="blog in blogs" :key="blog.id" class="profile-note-card" @click="router.push(`/blogs/${blog.id}`)">
              <img :src="imageUrl(blog.images)" :alt="blog.title" />
              <div class="profile-note-content">
                <h3>{{ blog.title }}</h3>
                <div class="profile-note-stats">
                  <span class="note-like" :class="{ active: Number(blog.liked) > 0 }" aria-label="点赞数量"><b>♥</b>{{ blog.liked || 0 }}</span>
                  <span class="note-comment" aria-label="评论数量"><b>◯</b>{{ blog.comments || 0 }}</span>
                </div>
              </div>
              <span class="profile-note-arrow">→</span>
            </article>
          </div>
          <nav v-if="notesPages > 1" class="pagination" aria-label="我的笔记分页">
            <button :disabled="notesPage === 1" @click="changeNotesPage(notesPage - 1)">上一页</button>
            <button v-for="page in notesPages" :key="page" :class="{ active: page === notesPage }" @click="changeNotesPage(page)">{{ page }}</button>
            <button :disabled="notesPage === notesPages" @click="changeNotesPage(notesPage + 1)">下一页</button>
          </nav>
        </div>

        <div v-else class="profile-tab-panel">
          <div v-if="ordersLoading" class="empty-state">正在加载优惠券订单…</div>
          <div v-else-if="tabMessage" class="empty-state">{{ tabMessage }}</div>
          <div v-else-if="!orders.length" class="empty-state">还没有优惠券订单，去商家详情页看看吧。</div>
          <template v-else>
            <div class="order-list">
              <article v-for="order in orders" :key="order.id" class="order-card">
                <img :src="imageUrl(order.shopImage)" alt="商家图片" />
                <div class="order-main">
                  <span>{{ order.shopName }}</span>
                  <h3>{{ order.voucherTitle }}</h3>
                  <small>订单号：{{ order.id }} · {{ formatDate(order.createTime) }}</small>
                </div>
                <div class="order-state" :class="`status-${order.status}`">{{ orderStatus(order.status) }}</div>
                <router-link v-if="order.shopId" :to="`/shops/${order.shopId}`">查看商家 →</router-link>
              </article>
            </div>
            <nav v-if="ordersPages > 1" class="pagination" aria-label="优惠券订单分页">
              <button :disabled="ordersPage === 1" @click="changeOrdersPage(ordersPage - 1)">上一页</button>
              <button v-for="page in ordersPages" :key="page" :class="{ active: page === ordersPage }" @click="changeOrdersPage(page)">{{ page }}</button>
              <button :disabled="ordersPage === ordersPages" @click="changeOrdersPage(ordersPage + 1)">下一页</button>
            </nav>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>
