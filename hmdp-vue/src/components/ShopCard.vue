<script setup>
import { imageUrl } from '../utils/format'
defineProps({ shop: { type: Object, required: true } })
</script>

<template>
  <article class="shop-card" @click="$router.push(`/shops/${shop.id}`)">
    <img :src="imageUrl(shop.images)" />
    <div>
      <h3>{{ shop.name }}</h3>
      <div class="rating"><span>★★★★★</span> {{ shop.score ? (shop.score / 10).toFixed(1) : '暂无评分' }}</div>
      <p>{{ shop.area }} · {{ shop.address }}</p>
      <strong>人均 ¥{{ shop.avgPrice || '--' }}</strong>
      <small v-if="shop.distance">距离 {{ Math.round(shop.distance) }}m</small>
    </div>
  </article>
</template>
