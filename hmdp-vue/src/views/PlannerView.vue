<script setup>
import { computed, ref } from 'vue'

const cities = ['杭州', '上海', '北京', '成都', '广州']
const interests = ['咖啡甜品', '本地美食', '小众拍照', '夜间生活', '亲子友好', '文化街区']
const form = ref({ city: '杭州', day: '周六', duration: '一日', budget: '300', interests: ['咖啡甜品', '本地美食'] })
const loading = ref(false)
const plan = ref(null)
const conversationStarted = ref(false)

const selectedInterestText = computed(() => form.value.interests.join('、') || '城市漫游')

const toggleInterest = interest => {
  const selected = form.value.interests
  const index = selected.indexOf(interest)
  if (index >= 0) selected.splice(index, 1)
  else if (selected.length < 3) selected.push(interest)
}

const generatePlan = () => {
  conversationStarted.value = true
  loading.value = true
  plan.value = null
  setTimeout(() => {
    const city = form.value.city
    plan.value = {
      title: `${city} · ${form.value.day}松弛探店路线`,
      summary: `围绕${selectedInterestText.value}安排，预计步行 4.2 公里，消费约 ¥${form.value.budget}。路线尽量避开折返，并预留了休息时间。`,
      stops: [
        { time: '10:00', label: '第一站 · 慢慢醒来', name: `${city}巷口手作咖啡`, detail: '先用一杯手冲开启行程，建议停留 60 分钟', tag: '咖啡 · 安静' },
        { time: '12:00', label: '第二站 · 本地午餐', name: '老街烟火小馆', detail: '选择当地口味，人均约 ¥68，错峰到店更从容', tag: '本地菜 · 高口碑' },
        { time: '14:30', label: '第三站 · 城市漫游', name: '梧桐里文化街区', detail: '适合散步与拍照，沿途有独立书店和小展览', tag: '步行 · 拍照' },
        { time: '17:30', label: '收尾 · 日落甜点', name: '屋顶花园甜品室', detail: '在傍晚光线最好的时段抵达，为路线轻松收尾', tag: '甜品 · 日落' }
      ],
      tips: ['热门店建议提前 30 分钟线上取号', '路线以步行为主，穿舒适的鞋更合适', '下雨时可将第三站替换为城市美术馆']
    }
    loading.value = false
  }, 900)
}
</script>

<template>
  <div class="planner-page">
    <section class="planner-hero container">
      <div>
        <span class="eyebrow">CITYHUB AI PLANNER</span>
        <h1>把想去的地方<br />变成一条<em>刚刚好的路线</em></h1>
        <p>告诉我你的时间、预算和兴趣，智能助手会为你整理一份轻松、不绕路的探店计划。</p>
      </div>
      <div class="planner-orbit" aria-hidden="true"><span>吃</span><span>逛</span><span>拍</span><b>AI</b></div>
    </section>

    <section class="container planner-workspace">
      <aside class="planner-form-card">
        <div class="planner-card-head"><span>01</span><div><h2>告诉我你的偏好</h2><p>最多选择 3 个兴趣标签</p></div></div>

        <label>目的地
          <select v-model="form.city"><option v-for="city in cities" :key="city">{{ city }}</option></select>
        </label>
        <div class="planner-form-row">
          <label>出行日期<input v-model="form.day" placeholder="例如：周六" /></label>
          <label>游玩时长<select v-model="form.duration"><option>半日</option><option>一日</option><option>两日</option></select></label>
        </div>
        <label>人均预算
          <div class="budget-input"><span>¥</span><input v-model="form.budget" type="number" min="50" max="5000" /></div>
        </label>
        <div class="planner-field-title">感兴趣的体验</div>
        <div class="interest-tags">
          <button v-for="interest in interests" :key="interest" type="button" :class="{ active: form.interests.includes(interest) }" @click="toggleInterest(interest)">{{ interest }}</button>
        </div>
        <button class="planner-generate" :disabled="loading" @click="generatePlan"><span>✦</span>{{ loading ? '正在规划路线…' : '生成我的探店计划' }}</button>
      </aside>

      <main class="planner-result-card">
        <div class="assistant-bar"><div class="assistant-avatar">C</div><div><strong>CityHub 规划助手</strong><span><i></i> 随时为你规划</span></div><b>智能路线 · 效果预览</b></div>

        <div v-if="!conversationStarted" class="planner-welcome">
          <div class="welcome-mark">✦</div>
          <h2>今天想去哪里走走？</h2>
          <p>完成左侧偏好后，我会从时间、距离和消费三个维度，为你组合一条舒适的路线。</p>
          <div class="sample-prompts"><span>“周六想在杭州喝咖啡、拍照”</span><span>“预算 300 元的一日美食路线”</span></div>
        </div>

        <div v-else class="planner-conversation">
          <div class="user-request"><span>我想在{{ form.city }}安排{{ form.duration }}行程，预算 ¥{{ form.budget }}，偏好{{ selectedInterestText }}。</span></div>
          <div v-if="loading" class="assistant-thinking"><div class="mini-avatar">C</div><div><i></i><i></i><i></i><span>正在计算更顺路的安排</span></div></div>

          <div v-if="plan" class="generated-plan">
            <div class="plan-intro"><span class="mini-avatar">C</span><div><small>已为你生成路线</small><h2>{{ plan.title }}</h2><p>{{ plan.summary }}</p></div></div>
            <div class="route-timeline">
              <article v-for="(stop, index) in plan.stops" :key="stop.time">
                <div class="route-time">{{ stop.time }}</div>
                <div class="route-node"><span>{{ index + 1 }}</span></div>
                <div class="route-place"><small>{{ stop.label }}</small><h3>{{ stop.name }}</h3><p>{{ stop.detail }}</p><b>{{ stop.tag }}</b></div>
              </article>
            </div>
            <div class="plan-tips"><strong>出发前提醒</strong><span v-for="tip in plan.tips" :key="tip">✓ {{ tip }}</span></div>
            <div class="plan-actions"><button @click="generatePlan">换一条路线</button><router-link to="/shops">查看相关商家 →</router-link></div>
          </div>
        </div>
      </main>
    </section>
  </div>
</template>
