<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from './components/AppHeader.vue'

const route = useRoute()
const bare = computed(() => route.meta.bare)
</script>

<template>
  <div class="app-shell">
    <AppHeader v-if="!bare" />
    <main :class="{ 'page-main': !bare }"><router-view /></main>
    <footer v-if="!bare" class="site-footer">CityHub · 发现值得奔赴的城市生活</footer>
  </div>
</template>
