<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '../api/http'
import { formatDate, imageUrl } from '../utils/format'

const route = useRoute()
const router = useRouter()
const blog = ref({})
const shop = ref(null)
const liked = ref(false)
const likedUsers = ref([])
const currentUser = ref(null)
const commentText = ref('')
const commentImage = ref(null)
const commentImageUrl = ref('')
const commentFileInput = ref(null)
const commentNotice = ref({ message: '', type: 'error' })
const comments = ref([])
const submittingComment = ref(false)
const replyTarget = ref(null)
const commentTextarea = ref(null)
let commentNoticeTimer = null

const showCommentNotice = (message, type = 'error') => {
  commentNotice.value = { message, type }
  if (commentNoticeTimer) clearTimeout(commentNoticeTimer)
  commentNoticeTimer = setTimeout(() => {
    commentNotice.value = { message: '', type }
  }, 3000)
}

const load = async () => {
  blog.value = await http.get(`/blog/${route.params.id}`)
  liked.value = Boolean(blog.value.isLike)

  const tasks = [
    http.get(`/blog/likes/${route.params.id}`).then(data => { likedUsers.value = data || [] }).catch(() => {}),
    http.get(`/blog-comments/of/blog/${route.params.id}`).then(data => { comments.value = data || [] }).catch(() => {})
  ]
  if (blog.value.shopId) {
    tasks.push(http.get(`/shop/${blog.value.shopId}`).then(data => { shop.value = data }))
  }
  if (sessionStorage.getItem('token')) {
    tasks.push(http.get('/user/me').then(data => { currentUser.value = data }).catch(() => {}))
  }
  await Promise.all(tasks)
}

const like = async () => {
  if (!sessionStorage.getItem('token')) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  await http.put(`/blog/like/${blog.value.id}`)
  liked.value = !liked.value
  blog.value.liked = Math.max(0, Number(blog.value.liked || 0) + (liked.value ? 1 : -1))
  likedUsers.value = await http.get(`/blog/likes/${blog.value.id}`).catch(() => likedUsers.value) || []
}

const selectCommentImage = event => {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    showCommentNotice('请选择图片文件')
    event.target.value = ''
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    showCommentNotice('评论图片不能超过 10MB')
    event.target.value = ''
    return
  }
  removeCommentImage()
  commentImage.value = file
  commentImageUrl.value = URL.createObjectURL(file)
}

const removeCommentImage = () => {
  if (commentImageUrl.value) URL.revokeObjectURL(commentImageUrl.value)
  commentImage.value = null
  commentImageUrl.value = ''
  if (commentFileInput.value) commentFileInput.value.value = ''
}

const startReply = comment => {
  if (!sessionStorage.getItem('token')) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  replyTarget.value = comment
  document.querySelector('.comment-composer')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  setTimeout(() => commentTextarea.value?.focus(), 350)
}

const cancelReply = () => {
  replyTarget.value = null
}

const likeComment = async comment => {
  if (!sessionStorage.getItem('token')) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  const isLike = await http.put(`/blog-comments/like/${comment.id}`)
  comment.isLike = Boolean(isLike)
  comment.liked = Math.max(0, Number(comment.liked || 0) + (comment.isLike ? 1 : -1))
}

const submitComment = async () => {
  if (!sessionStorage.getItem('token')) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (!commentText.value.trim()) {
    showCommentNotice('请先写下你的真实感受')
    return
  }
  submittingComment.value = true
  let uploadedName = ''
  try {
    let images = ''
    if (commentImage.value) {
      const body = new FormData()
      body.append('file', commentImage.value)
      uploadedName = await http.post('/upload/comment', body)
      images = `/imgs${uploadedName}`
    }
    await http.post('/blog-comments', {
      blogId: Number(route.params.id),
      content: commentText.value.trim(),
      images,
      replyToId: replyTarget.value?.id || null
    })
    comments.value = await http.get(`/blog-comments/of/blog/${route.params.id}`) || []
    blog.value.comments = Number(blog.value.comments || 0) + 1
    commentText.value = ''
    cancelReply()
    removeCommentImage()
    showCommentNotice('已发表评论', 'success')
  } catch (error) {
    if (uploadedName) {
      await http.get('/upload/comment/delete', { params: { name: uploadedName } }).catch(() => {})
    }
    showCommentNotice(error.message)
  } finally {
    submittingComment.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => {
  removeCommentImage()
  if (commentNoticeTimer) clearTimeout(commentNoticeTimer)
})
</script>

<template>
  <div class="container article-page">
    <Transition name="comment-toast">
      <div v-if="commentNotice.message" class="comment-toast" :class="`comment-toast-${commentNotice.type}`" role="status">
        <span>{{ commentNotice.type === 'success' ? '✓' : '!' }}</span>
        <strong>{{ commentNotice.message }}</strong>
      </div>
    </Transition>
    <article class="article">
      <div class="article-author" @click="router.push(`/users/${blog.userId}`)">
        <img :src="imageUrl(blog.icon)" alt="作者头像" />
        <div><strong>{{ blog.name }}</strong><span>{{ formatDate(blog.createTime) }}</span></div>
      </div>
      <h1>{{ blog.title }}</h1>
      <div class="article-images"><img v-for="img in blog.images?.split(',')" :key="img" :src="img" alt="探店笔记图片" /></div>
      <p class="article-copy">{{ blog.content }}</p>

      <div v-if="shop" class="attached-shop" @click="router.push(`/shops/${shop.id}`)">
        <img :src="imageUrl(shop.images)" alt="商家图片" />
        <div><small>笔记提到的商家</small><strong>{{ shop.name }}</strong><span>{{ shop.address }}</span></div>
        <b>查看商家 →</b>
      </div>

      <section class="article-social">
        <button class="like-button" :class="{ active: liked }" @click="like">♥ {{ blog.liked || 0 }} 人点赞</button>
        <div class="liked-summary">
          <div v-if="likedUsers.length" class="liked-avatars">
            <img v-for="user in likedUsers" :key="user.id" :src="imageUrl(user.icon)" :alt="user.nickName" :title="user.nickName" />
          </div>
          <span v-if="Number(blog.liked) > 0"><strong>{{ blog.liked }}</strong> 位用户觉得这篇笔记值得点赞</span>
          <span v-else>还没有人点赞，成为第一个点赞的人吧</span>
        </div>
      </section>

      <section class="comment-section">
        <div class="comment-heading">
          <div><span>COMMUNITY REVIEWS</span><h2>网友评价</h2></div>
          <p>分享你的真实体验，让每一次城市探索更有价值。</p>
        </div>

        <div v-if="replyTarget" class="reply-context">
          <span>正在回复 <strong>{{ replyTarget.userName || 'CityHub 用户' }}</strong></span>
          <button type="button" @click="cancelReply">取消回复</button>
        </div>
        <div class="comment-composer">
          <img class="comment-avatar" :src="imageUrl(currentUser?.icon)" alt="当前用户头像" />
          <div class="comment-editor">
            <textarea ref="commentTextarea" v-model="commentText" maxlength="500" rows="5" :placeholder="replyTarget ? `回复 ${replyTarget.userName || '该用户'}…` : '说说你的看法，友善交流会让社区更美好…'"></textarea>
            <div v-if="commentImageUrl" class="comment-image-preview">
              <img :src="commentImageUrl" alt="待上传的评论图片" />
              <button type="button" aria-label="移除评论图片" @click="removeCommentImage">×</button>
            </div>
            <div class="comment-tools">
              <button type="button" class="comment-upload" title="上传评论图片" @click="commentFileInput.click()">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h3l1.4-2h7.2L17 7h3v12H4z"/><circle cx="12" cy="13" r="3.5"/></svg>
                <span>{{ commentImage ? '更换图片' : '添加图片' }}</span>
              </button>
              <input ref="commentFileInput" type="file" accept="image/*" hidden @change="selectCommentImage" />
              <span class="comment-count">{{ commentText.length }}/500</span>
            </div>
          </div>
        </div>
        <div class="comment-submit-row">
          <span>请尊重他人，并对自己的评价负责</span>
          <button type="button" class="comment-submit" :disabled="submittingComment" @click="submitComment">{{ submittingComment ? '发表中…' : '发表评论' }}</button>
        </div>

        <div class="comment-list-heading"><strong>全部评价</strong><span>{{ blog.comments || 0 }} 条</span></div>
        <div v-if="!comments.length" class="comment-empty">还没有评价，来分享第一条真实感受吧。</div>
        <div v-else class="comment-list">
          <article v-for="comment in comments" :key="comment.id" class="comment-item">
            <img class="comment-user-avatar" :src="imageUrl(comment.userIcon)" :alt="comment.userName" />
            <div class="comment-body">
              <div class="comment-meta"><strong>{{ comment.userName || 'CityHub 用户' }}</strong><time>{{ formatDate(comment.createTime) }}</time></div>
              <p>{{ comment.content }}</p>
              <div v-if="comment.images" class="comment-pictures">
                <img v-for="img in comment.images.split(',')" :key="img" :src="img" alt="评论图片" />
              </div>
              <div class="comment-actions">
                <button type="button" class="comment-reply-btn" @click="startReply(comment)">回复</button>
                <button type="button" class="comment-like-btn" :class="{ active: Number(comment.liked) > 0 }" @click="likeComment(comment)"><b>♥</b><span>{{ comment.liked || 0 }}</span></button>
              </div>

              <div v-if="comment.replies?.length" class="comment-replies">
                <article v-for="reply in comment.replies" :key="reply.id" class="comment-reply-item">
                  <img :src="imageUrl(reply.userIcon)" :alt="reply.userName" />
                  <div>
                    <div class="comment-meta"><strong>{{ reply.userName || 'CityHub 用户' }}</strong><time>{{ formatDate(reply.createTime) }}</time></div>
                    <p><span v-if="reply.replyToUserName" class="reply-to">回复 @{{ reply.replyToUserName }}：</span>{{ reply.content }}</p>
                    <div v-if="reply.images" class="comment-pictures"><img v-for="img in reply.images.split(',')" :key="img" :src="img" alt="回复图片" /></div>
                    <div class="comment-actions">
                      <button type="button" class="comment-reply-btn" @click="startReply(reply)">回复</button>
                      <button type="button" class="comment-like-btn" :class="{ active: Number(reply.liked) > 0 }" @click="likeComment(reply)"><b>♥</b><span>{{ reply.liked || 0 }}</span></button>
                    </div>
                  </div>
                </article>
              </div>
            </div>
          </article>
        </div>
      </section>
    </article>
  </div>
</template>
