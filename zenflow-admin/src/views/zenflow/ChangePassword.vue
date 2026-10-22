<script setup lang="ts">
import {reactive,ref} from 'vue';
import {ElMessage} from 'element-plus';
import {api,errorText} from '../../api/zenflow/client';
import {useIdentity} from '../../stores/identity';
const open=ref(false),saving=ref(false),form=reactive({currentPassword:'',newPassword:'',confirm:''});
function clear(){Object.assign(form,{currentPassword:'',newPassword:'',confirm:''});}
async function save(){
 if(!form.currentPassword){ElMessage.warning('请输入当前密码');return;}
 if(form.newPassword.length<10||new TextEncoder().encode(form.newPassword).length>72){ElMessage.warning('新密码至少10个字符，最多72个UTF-8字节');return;}
 if(form.newPassword!==form.confirm){ElMessage.warning('两次输入的新密码不一致');return;}
 saving.value=true;
 try{await api.post('/account/password',{currentPassword:form.currentPassword,newPassword:form.newPassword});open.value=false;clear();await useIdentity().logout();ElMessage.success('密码已修改，请用新密码重新登录');}
 catch(e){ElMessage.error(errorText(e));}finally{saving.value=false;}
}
</script>
<template><el-button text @click="open=true">修改密码</el-button><el-dialog v-model="open" title="修改管理员密码" width="min(460px,94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @closed="clear"><el-form label-position="top" @submit.prevent="save"><el-form-item label="当前密码"><el-input v-model="form.currentPassword" type="password" autocomplete="current-password" show-password maxlength="128"/></el-form-item><el-form-item label="新密码"><el-input v-model="form.newPassword" type="password" autocomplete="new-password" show-password maxlength="128"/></el-form-item><el-form-item label="确认新密码"><el-input v-model="form.confirm" type="password" autocomplete="new-password" show-password maxlength="128" @keyup.enter="save"/></el-form-item></el-form><p class="muted">至少10个字符；修改后管理员的已有登录会失效，需要重新登录。</p><template #footer><el-button :disabled="saving" @click="open=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存新密码</el-button></template></el-dialog></template>
