<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { publishBlog } from '../api/blog'
import { imageUrl } from '../utils/format'

const router = useRouter()
const form = reactive({ title: '', content: '', shopId: null })
const shops = ref([])
const shopDropdownOpen = ref(false)
const selectedShop = ref(null)
const uploadedImages = ref([])
const uploading = ref(false)
const publishing = ref(false)
const message = ref('')
const fileInput = ref(null)

const canUpload = computed(() => uploadedImages.value.length < 9 && !uploading.value)

const loadShops = async () => {
  try {
    shops.value = await http.get('/shop/of/name', { params: { current: 1 } }) || []
  } catch (error) {
    message.value = error.message
  }
}

const toggleShopDropdown = () => {
  shopDropdownOpen.value = !shopDropdownOpen.value
}

const chooseShop = shop => {
  form.shopId = shop.id
  selectedShop.value = shop
  shopDropdownOpen.value = false
}

const uploadFiles = async event => {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  if (files.length + uploadedImages.value.length > 9) {
    message.value = '最多上传 9 张图片'
    event.target.value = ''
    return
  }
  const invalid = files.find(file => !file.type.startsWith('image/') || file.size > 10 * 1024 * 1024)
  if (invalid) {
    message.value = '仅支持 10MB 以内的图片文件'
    event.target.value = ''
    return
  }

  uploading.value = true
  message.value = ''
  try {
    for (const file of files) {
      const body = new FormData()
      body.append('file', file)
      const name = await http.post('/upload/blog', body)
      uploadedImages.value.push({ name, url: `/imgs${name}` })
    }
  } catch (error) {
    message.value = `图片上传失败：${error.message}`
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

const removeImage = async image => {
  try {
    await http.get('/upload/blog/delete', { params: { name: image.name } })
    uploadedImages.value = uploadedImages.value.filter(item => item.name !== image.name)
  } catch (error) {
    message.value = `删除图片失败：${error.message}`
  }
}

const submit = async () => {
  if (!form.title.trim()) return message.value = '请输入笔记标题'
  if (!form.content.trim()) return message.value = '请输入笔记内容'
  if (!form.shopId) return message.value = '请选择关联商家'
  if (!uploadedImages.value.length) return message.value = '请至少上传一张图片'

  publishing.value = true
  message.value = ''
  try {
    // BlogController 接收的就是 Blog JSON；userId 由 BlogService 根据 token 设置。
    const blogId = await publishBlog({
      title: form.title.trim(),
      content: form.content.trim(),
      shopId: form.shopId,
      images: uploadedImages.value.map(image => image.url).join(',')
    })
    message.value = '发布成功，正在打开笔记…'
    await router.push(`/blogs/${blogId}`)
  } catch (error) {
    message.value = error.message
  } finally {
    publishing.value = false
  }
}

onMounted(loadShops)
</script>

<template>
  <div class="container editor-page">
    <div class="page-title">
      <span class="eyebrow">SHARE A STORY</span>
      <h1>发布探店笔记</h1>
      <p>把一次真实体验，变成别人下一次出发的理由。</p>
    </div>

    <div class="editor-layout">
      <section class="editor-card">
        <label>标题
          <input v-model="form.title" maxlength="128" placeholder="给这次发现起个吸引人的标题" />
        </label>
        <label>内容
          <textarea v-model="form.content" rows="10" placeholder="环境、口味、服务，聊聊真实感受…"></textarea>
        </label>

        <div class="field-label">探店图片 <span>{{ uploadedImages.length }}/9</span></div>
        <div class="upload-grid">
          <div v-for="image in uploadedImages" :key="image.name" class="upload-preview">
            <img :src="image.url" alt="已上传的探店图片" />
            <button type="button" aria-label="删除图片" @click="removeImage(image)">×</button>
          </div>
          <button v-if="canUpload" type="button" class="upload-trigger" @click="fileInput.click()">
            <strong>＋</strong><span>{{ uploading ? '上传中…' : '上传图片' }}</span>
          </button>
          <input ref="fileInput" class="file-input" type="file" accept="image/*" multiple @change="uploadFiles" />
        </div>
        <p class="field-help">支持 JPG、PNG、WebP 等图片格式，单张不超过 10MB，最多 9 张。</p>

        <div class="field-label">关联商家</div>
        <div class="shop-selector">
          <button type="button" class="shop-select-trigger" :class="{ active: shopDropdownOpen }" @click="toggleShopDropdown">
            <span>{{ selectedShop ? selectedShop.name : '选择关联商家' }}</span>
            <b>{{ shopDropdownOpen ? '▲' : '▼' }}</b>
          </button>
          <div v-if="shopDropdownOpen" class="shop-dropdown">
            <button v-for="shop in shops" :key="shop.id" type="button" @click="chooseShop(shop)">
              <img :src="imageUrl(shop.images)" />
              <span><strong>{{ shop.name }}</strong><small>{{ shop.area }} · {{ shop.address }}</small></span>
              <b v-if="form.shopId === shop.id">✓</b>
            </button>
            <p v-if="!shops.length">没有找到相关商家</p>
          </div>
        </div>
        <div v-if="selectedShop" class="selected-shop">
          <span>已选择</span><strong>{{ selectedShop.name }}</strong><button type="button" @click="form.shopId = null; selectedShop = null; shopDropdownOpen = true">重新选择</button>
        </div>

        <p v-if="message" class="form-message">{{ message }}</p>
        <button class="submit-btn" :disabled="publishing || uploading" @click="submit">{{ publishing ? '发布中…' : '发布笔记' }}</button>
      </section>

      <aside class="editor-tip">
        <span>写作提示</span><h3>一篇好笔记，始于真实</h3>
        <ul><li>说清楚你为什么喜欢或不喜欢</li><li>提供价格、位置等实用信息</li><li>使用自己拍摄的清晰图片</li></ul>
      </aside>
    </div>
  </div>
</template>
