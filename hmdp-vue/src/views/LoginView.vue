<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '../api/http'

const route = useRoute(), router = useRouter()
const mode = ref('code'), busy = ref(false), message = ref('')
const form = reactive({ phone: '', code: '', password: '' })

const sendCode = async () => {
  try { await http.post('/user/code', null, { params: { phone: form.phone } }); message.value = '验证码已发送' }
  catch (e) { message.value = e.message }
}
const login = async () => {
  busy.value = true
  try {
    const url = mode.value === 'code' ? '/user/login' : '/user/login/password'
    const token = await http.post(url, mode.value === 'code' ? { phone: form.phone, code: form.code } : { phone: form.phone, password: form.password })
    sessionStorage.setItem('token', token)
    router.replace(route.query.redirect || '/')
  } catch (e) { message.value = e.message }
  finally { busy.value = false }
}
</script>

<template>
  <div class="login-page">
    <div class="login-visual"><router-link class="brand light" to="/"><span class="brand-mark">C</span>CityHub</router-link><div><span class="eyebrow">WELCOME BACK</span><h1>重新连接<br />你的城市生活</h1><p>登录后收藏好店、分享体验，也不错过每一次限时优惠。</p></div><small>© 2026 CityHub</small></div>
    <div class="login-panel"><div class="login-form"><h2>欢迎回来</h2><p>使用手机号登录你的账户</p><div class="mode-tabs"><button :class="{active: mode==='code'}" @click="mode='code'">验证码登录</button><button :class="{active: mode==='password'}" @click="mode='password'">密码登录</button></div><label>手机号<input v-model="form.phone" maxlength="11" placeholder="请输入手机号" /></label><label v-if="mode==='code'">验证码<div class="code-input"><input v-model="form.code" placeholder="6 位验证码" /><button @click="sendCode">获取验证码</button></div></label><label v-else>密码<input v-model="form.password" type="password" placeholder="请输入密码" /></label><p v-if="message" class="form-message">{{ message }}</p><button class="submit-btn" :disabled="busy" @click="login">{{ busy ? '登录中…' : '登录' }}</button><span class="agreement">登录即表示同意《用户协议》和《隐私政策》</span></div></div>
  </div>
</template>
