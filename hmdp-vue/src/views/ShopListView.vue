<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '../api/http'
import ShopCard from '../components/ShopCard.vue'

const route = useRoute()
const router = useRouter()
const types = ref([])
const shops = ref([])
const activeType = ref(Number(route.query.type) || 1)
const current = ref(1)
const loading = ref(false)

const loadTypes = async () => { types.value = await http.get('/shop-type/list') }
const loadShops = async (reset = false) => {
  if (reset) { current.value = 1; shops.value = [] }
  loading.value = true
  try {
    const data = await http.get('/shop/of/type', { params: { typeId: activeType.value, current: current.value } })
    shops.value.push(...(data || []))
    current.value++
  } finally { loading.value = false }
}
const chooseType = id => router.push({ query: { type: id } })

loadTypes()
watch(() => route.query.type, value => { activeType.value = Number(value) || 1; loadShops(true) }, { immediate: true })
</script>

<template>
  <div class="container listing-page">
    <div class="page-title"><span class="eyebrow">LOCAL GUIDE</span><h1>发现附近好店</h1><p>真实口碑，帮你找到下一家想去的店。</p></div>
    <div class="filter-bar">
      <button v-for="type in types" :key="type.id" :class="{ active: activeType === type.id }" @click="chooseType(type.id)">{{ type.name }}</button>
    </div>
    <div class="shop-grid"><ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" /></div>
    <button class="load-more" :disabled="loading" @click="loadShops()">{{ loading ? '加载中…' : '加载更多' }}</button>
  </div>
</template>
