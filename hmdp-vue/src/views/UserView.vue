<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import http from '../api/http'
import BlogCard from '../components/BlogCard.vue'
import { imageUrl } from '../utils/format'
const route=useRoute(), user=ref({}), info=ref({}), blogs=ref([]), followed=ref(false)
onMounted(async()=>{const id=route.params.id;[user.value,info.value,blogs.value]=await Promise.all([http.get(`/user/${id}`),http.get(`/user/info/${id}`),http.get('/blog/of/user',{params:{id,current:1}})]);if(sessionStorage.getItem('token')) followed.value=await http.get(`/follow/or/not/${id}`)})
const follow=async()=>{await http.put(`/follow/${route.params.id}/${!followed.value}`);followed.value=!followed.value}
</script>
<template><div class="container profile-page"><section class="profile-banner"><div class="profile-main"><img :src="imageUrl(user.icon)"/><div><h1>{{user.nickName}}</h1><p>{{info.introduce||'还没有个人简介'}}</p></div></div><button class="primary-btn" @click="follow">{{followed?'已关注':'关注'}}</button></section><section class="profile-content"><div class="section-head"><div><span class="eyebrow">CREATOR STORIES</span><h2>TA 的探店笔记</h2></div></div><div class="blog-grid"><BlogCard v-for="blog in blogs" :key="blog.id" :blog="blog"/></div></section></div></template>
