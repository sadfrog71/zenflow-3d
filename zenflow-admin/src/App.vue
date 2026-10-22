<script setup lang="ts">
import { ref,onMounted,defineAsyncComponent } from 'vue';
import { ElMessage } from 'element-plus';
import { useIdentity } from './stores/identity';
import Models from './views/zenflow/Models.vue';
import Companion from './views/zenflow/Companion.vue';
import Operations from './views/zenflow/Operations.vue';
import References from './views/zenflow/References.vue';
const Books = defineAsyncComponent(() => import('./views/zenflow/Books.vue'));
import Community from './views/zenflow/Community.vue';
import HomeSlogans from './views/zenflow/HomeSlogans.vue';
import Users from './views/zenflow/Users.vue';
import ChangePassword from './views/zenflow/ChangePassword.vue';
const identity=useIdentity();const ready=ref(false);const section=ref('models');
onMounted(async()=>{try{await identity.restore();}catch{ElMessage.error('登录未完成，请重新登录');}finally{ready.value=true;}});
</script>
<template>
 <div class="shell"><header><div class="brand"><span class="enso"></span><div>此间 <small>INVELLE · 管理后台</small></div></div><div v-if="identity.username" class="account">{{identity.username}}<ChangePassword/><el-button text @click="identity.logout">退出</el-button></div></header>
 <main v-if="!ready" class="welcome">正在加载…</main>
 <main v-else-if="!identity.username" class="welcome"><div class="eyebrow">INVELLE WORKSPACE</div><h1>让陪伴，在此间发生。</h1><p>配置模型、维护陪伴原则，查看服务状态。</p><el-button type="primary" size="large" @click="identity.login">管理员登录</el-button><p class="muted">专用运营空间 · 个人对话和记录保持私有</p></main>
 <div v-else class="workspace"><aside><button :class="{selected:section==='users'}" @click="section='users'">用户管理</button><button :class="{selected:section==='slogans'}" @click="section='slogans'">首页标语库</button><button :class="{selected:section==='models'}" @click="section='models'">模型配置</button><button :class="{selected:section==='references'}" @click="section='references'">参考资料</button><button :class="{selected:section==='books'}" @click="section='books'">书籍管理</button><button :class="{selected:section==='examples'}" @click="section='examples'">回答示例</button><button :class="{selected:section==='companion'}" @click="section='companion'">陪伴配置</button><button :class="{selected:section==='community'}" @click="section='community'">创作社区</button><button :class="{selected:section==='operations'}" @click="section='operations'">服务状态</button></aside><main class="content"><Models v-if="section==='models'"/><References v-else-if="section==='references'" standalone/><Books v-else-if="section==='books'"/><Companion v-else-if="section==='companion'||section==='examples'" :key="section" :initial-tab="section==='examples'?'examples':'principles'"/><Users v-else-if="section==='users'"/><HomeSlogans v-else-if="section==='slogans'"/><Community v-else-if="section==='community'"/><Operations v-else/></main></div>
 <footer>此间 / Invelle · 独立测试环境</footer></div>
</template>
