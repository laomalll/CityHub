<script setup>
import { imageUrl } from '../utils/format'
defineProps({ blog: { type: Object, required: true } })
</script>

<template>
  <article class="blog-card" @click="$router.push(`/blogs/${blog.id}`)">
    <img class="blog-cover" :src="imageUrl(blog.images)" />
    <div class="blog-body">
      <h3>{{ blog.title }}</h3>
      <p>{{ blog.content }}</p>
      <div class="blog-meta">
        <span class="author"><img :src="imageUrl(blog.icon)" />{{ blog.name }}</span>
        <span>♡ {{ blog.liked || 0 }}</span>
      </div>
    </div>
  </article>
</template>
