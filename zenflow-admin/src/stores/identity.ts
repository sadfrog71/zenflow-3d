import { defineStore } from 'pinia';
import { ref } from 'vue';
import axios from 'axios';

export const useIdentity = defineStore('identity',()=>{
 const token=ref(''); const username=ref('');
 let pendingRefresh:Promise<boolean>|undefined;
 function clear(){token.value='';username.value='';sessionStorage.removeItem('invelle.admin.refresh');}
 async function refresh():Promise<boolean>{
  if(pendingRefresh)return pendingRefresh;
  const prior=sessionStorage.getItem('invelle.admin.refresh');if(!prior){clear();return false;}
  pendingRefresh=(async()=>{try{
   const response=await axios.post('/api/admin/oauth2/token',new URLSearchParams({grant_type:'refresh_token',client_id:'zenflow-admin',refresh_token:prior}));
   // A logout or another login must not be overwritten by an older response.
   if(sessionStorage.getItem('invelle.admin.refresh')!==prior)return false;
   token.value=response.data.access_token;sessionStorage.setItem('invelle.admin.refresh',response.data.refresh_token||prior);return true;
  }catch(error){if(axios.isAxiosError(error)&&[400,401,403].includes(error.response?.status||0))clear();throw error;}
  finally{pendingRefresh=undefined;}})();
  return pendingRefresh;
 }
 async function login(){
  const random=(size:number)=>Array.from(crypto.getRandomValues(new Uint8Array(size)),b=>b.toString(16).padStart(2,'0')).join('');
  const verifier=random(32),state=random(20);
  const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(verifier));
  const challenge=btoa(String.fromCharCode(...new Uint8Array(digest))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
  sessionStorage.setItem('invelle.admin.pkce',JSON.stringify({verifier,state}));
  const url=new URL('/api/admin/oauth2/authorize',location.origin);
  url.search=new URLSearchParams({client_id:'zenflow-admin',response_type:'code',redirect_uri:location.origin+'/manage/callback',scope:'zenflow-admin',code_challenge:challenge,code_challenge_method:'S256',state,prompt:'login'}).toString();
  location.assign(url.toString());
 }
 async function restore(){
  const query=new URLSearchParams(location.search);
  if(location.pathname==='/manage/callback'){
   const saved=JSON.parse(sessionStorage.getItem('invelle.admin.pkce')||'null') as {verifier:string;state:string}|null;
   history.replaceState(null,'','/manage/');sessionStorage.removeItem('invelle.admin.pkce');
   if(!saved || query.get('state')!==saved.state || !query.get('code')) throw new Error('登录验证失败，请重新登录');
   const response=await axios.post('/api/admin/oauth2/token',new URLSearchParams({grant_type:'authorization_code',client_id:'zenflow-admin',code:query.get('code')!,redirect_uri:location.origin+'/manage/callback',code_verifier:saved.verifier}));
   token.value=response.data.access_token;
   if(response.data.refresh_token) sessionStorage.setItem('invelle.admin.refresh',response.data.refresh_token);
  }else{
   if(!sessionStorage.getItem('invelle.admin.refresh'))return;
   if(!await refresh())return;
  }
  const me=await axios.get('/api/zenflow-admin/v1/me',{headers:{Authorization:'Bearer '+token.value}});username.value=me.data.username;
 }
 async function logout(){const prior=token.value;clear();if(prior) await axios.post('/api/zenflow-admin/v1/logout',{}, {headers:{Authorization:'Bearer '+prior}}).catch(()=>{});}
 return {token,username,login,restore,logout,refresh};
});
