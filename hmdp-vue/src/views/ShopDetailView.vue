<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '../api/http'
import { formatPrice, toTimestamp } from '../utils/format'

const route = useRoute()
const router = useRouter()
const shop = ref({})
const vouchers = ref([])
const notice = ref('')
const noticeType = ref('info')
const purchasingIds = ref(new Set())
let noticeTimer
const images = computed(() => shop.value.images?.split(',').filter(Boolean) || [])
const visibleVouchers = computed(() => vouchers.value.filter(v => v.type !== 1 || toTimestamp(v.endTime) >= Date.now()))

const formatVoucherTime = value => {
  const timestamp = toTimestamp(value)
  if (!Number.isFinite(timestamp)) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(new Date(timestamp))
}

const discountText = voucher => {
  const salePrice = Number(voucher.payValue)
  const originalPrice = Number(voucher.actualValue)
  if (!Number.isFinite(salePrice) || !Number.isFinite(originalPrice) || originalPrice <= 0) return '--'
  // 折数 = 实际支付价格 / 券面原价 * 10，例如 50 / 100 * 10 = 5 折。
  const discount = salePrice / originalPrice * 10
  return `${discount.toFixed(1).replace(/\.0$/, '')} 折`
}

const isPurchasing = id => purchasingIds.value.has(id)

const showNotice = (message, type = 'info') => {
  notice.value = message
  noticeType.value = type
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => { notice.value = '' }, 4000)
}

onMounted(async () => {
  ;[shop.value, vouchers.value] = await Promise.all([
    http.get(`/shop/${route.params.id}`),
    http.get(`/voucher/list/${route.params.id}`)
  ])
})

const seckill = async voucher => {
  if (!sessionStorage.getItem('token')) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (toTimestamp(voucher.beginTime) > Date.now()) return showNotice('秒杀活动尚未开始', 'warning')
  if (voucher.stock < 1) return showNotice('优惠券库存不足', 'warning')
  if (isPurchasing(voucher.id)) return
  purchasingIds.value.add(voucher.id)
  try {
    const orderId = await http.post(`/voucher-order/seckill/${voucher.id}`)
    // Lua 资格预检成功时 Redis 库存已经扣减，立即更新本地响应式数据。
    voucher.stock = Math.max(0, Number(voucher.stock) - 1)
    showNotice(`抢购成功，订单号：${orderId}`, 'success')
  } catch (error) { showNotice(error.message, 'error') }
  finally { purchasingIds.value.delete(voucher.id) }
}

const buyNormal = () => { showNotice('普通优惠券购买功能尚未接入后端', 'warning') }
</script>

<template>
  <div class="container detail-page">
    <Transition name="toast">
      <div v-if="notice" class="toast-notice" :class="`toast-${noticeType}`" role="status">
        <span class="toast-icon">{{ noticeType === 'success' ? '✓' : '!' }}</span>
        <strong>{{ notice }}</strong>
        <button aria-label="关闭提示" @click="notice = ''">×</button>
      </div>
    </Transition>
    <div class="breadcrumbs"><router-link to="/shops">商家列表</router-link><span>/</span><span>{{ shop.name }}</span></div>
    <section class="shop-hero">
      <div class="shop-gallery"><img v-for="(img, index) in images.slice(0, 3)" :key="img" :class="`gallery-${index}`" :src="img" /></div>
      <div class="shop-summary">
        <span class="shop-tag">本地精选</span><h1>{{ shop.name }}</h1>
        <div class="large-rating">★★★★★ <strong>{{ shop.score ? (shop.score / 10).toFixed(1) : '--' }}</strong></div>
        <p>{{ shop.area }} · {{ shop.address }}</p><p>营业时间：{{ shop.openHours || '以商家公布为准' }}</p>
        <div class="summary-data"><div><strong>¥{{ shop.avgPrice || '--' }}</strong><span>人均消费</span></div><div><strong>{{ shop.sold || 0 }}</strong><span>近期已售</span></div><div><strong>{{ shop.comments || 0 }}</strong><span>用户评价</span></div></div>
      </div>
    </section>

    <section class="voucher-section">
      <div class="section-head"><div><span class="eyebrow">SPECIAL OFFERS</span><h2>店内优惠</h2></div></div>
      <div v-if="!visibleVouchers.length" class="empty-state">暂无可领取优惠券</div>
      <div class="voucher-list">
        <article v-for="voucher in visibleVouchers" :key="voucher.id" class="voucher-card">
          <div class="voucher-value"><small>¥</small>{{ formatPrice(voucher.payValue) }}</div>
          <div class="voucher-copy">
            <div class="voucher-title-row"><h3>{{ voucher.title }}</h3><strong class="discount-badge">{{ discountText(voucher) }}</strong></div>
            <p>{{ voucher.subTitle }}</p>
            <span>原价：¥{{ formatPrice(voucher.actualValue) }}</span>
            <div v-if="voucher.type === 1" class="seckill-period">
              <span class="period-label">抢购时间</span>
              <strong>{{ formatVoucherTime(voucher.beginTime) }} — {{ formatVoucherTime(voucher.endTime) }}</strong>
            </div>
          </div>
          <div class="voucher-action" v-if="voucher.type === 1">
            <div class="stock-display"><span>剩余</span><strong>{{ voucher.stock }}</strong><span>张</span></div>
            <button :disabled="voucher.stock < 1 || toTimestamp(voucher.beginTime) > Date.now() || isPurchasing(voucher.id)" @click="seckill(voucher)">{{ isPurchasing(voucher.id) ? '抢购中…' : (toTimestamp(voucher.beginTime) > Date.now() ? '即将开始' : '立即抢购') }}</button>
          </div>
          <div class="voucher-action" v-else><small>普通代金券</small><button @click="buyNormal(voucher)">立即购买</button></div>
        </article>
      </div>
    </section>
  </div>
</template>
