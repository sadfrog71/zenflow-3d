<script setup lang="ts">
import { ref,onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { api,errorText } from '../../api/zenflow/client';
interface Overview{checkedAt:string;modelConfigured:boolean;userCount:number;sessionCount:number;noteCount:number;companion:{status:string;version:number}[];cleanup:{status:string;total:number}[]}
const data=ref<Overview>(),busy=ref(false);
async function load(){busy.value=true;try{data.value=(await api.get('/operations')).data;}catch(e){ElMessage.error(errorText(e));}finally{busy.value=false;}}
onMounted(load);
</script>
<template><div v-loading="busy"><div class="page-heading"><div><div class="eyebrow">SERVICE OVERVIEW</div><h1>服务状态</h1><p class="muted">查看配置与处理状态，个人内容保持私有。</p></div><el-button :disabled="busy" @click="load">刷新</el-button></div><template v-if="data"><div class="models"><article class="model-card"><h2>模型</h2><el-tag :type="data.modelConfigured?'success':'warning'">{{data.modelConfigured?'已配置':'待配置'}}</el-tag><p class="muted">配置状态取自服务端；实时连通性请在模型页面测试。</p></article><article class="model-card"><h2>陪伴</h2><el-tag :type="data.companion[0]?.status==='ENABLED'?'success':'info'">{{data.companion[0]?.status==='ENABLED'?'已发布启用':'尚未启用'}}</el-tag><p class="muted">在陪伴页面编辑与发布原则。</p></article><article class="model-card"><h2>使用概况</h2><p>{{data.userCount}} 位用户 · {{data.sessionCount}} 个会话 · {{data.noteCount}} 条记录</p><p class="muted">仅统计数量。</p></article><article class="model-card"><h2>记录删除处理</h2><p v-if="!data.cleanup.length" class="muted">暂无删除任务。</p><p v-for="row in data.cleanup" :key="row.status">{{({SUCCEEDED:'已完成',PENDING:'等待处理',RUNNING:'处理中',DEAD:'需要排查'} as Record<string,string>)[row.status]||row.status}}：{{row.total}}</p></article></div><p class="muted">查询时间：{{new Date(data.checkedAt).toLocaleString()}}</p></template></div></template>
