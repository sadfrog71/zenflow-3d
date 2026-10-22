<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="referrer" content="strict-origin-when-cross-origin"><title>此间 · 登录</title>
<style>
:root{font-family:system-ui,-apple-system,sans-serif;color:#293e35;background:#f4f3ec}*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px}.card{width:min(100%,430px);background:#fffdf8;border:1px solid #e2e3d8;border-radius:24px;padding:32px;box-shadow:0 18px 48px #19372d12}.brand{font-size:27px;letter-spacing:.12em;margin:0}.sub{color:#708076;margin:4px 0 26px}h1{font-size:23px;margin:0 0 20px}label{display:block;margin:16px 0 7px;font-size:14px}input{width:100%;border:1px solid #cbd3c9;border-radius:10px;padding:13px;font:inherit;background:#fff}input:focus{outline:2px solid #6d9b83}button{font:inherit;cursor:pointer}.primary{width:100%;border:0;border-radius:11px;background:#365a49;color:white;padding:14px;margin-top:24px}.links{display:flex;justify-content:space-between;margin-top:20px}.links button,.lang{border:0;background:none;color:#365a49;padding:4px}.lang{float:right}.hint{font-size:13px;color:#69766d;line-height:1.5}.error{color:#a2342b;min-height:20px;font-size:14px}.success{color:#365a49}.hidden{display:none}
</style>
</head>
<body>
<main class="card">
<button class="lang" id="lang" type="button">EN</button><p class="brand">此间</p><p class="sub">INVELLE</p>
<section id="loginPanel"><h1 data-zh="回来坐坐" data-en="Welcome back">回来坐坐</h1>
<form method="post" action="${formAction?html}" autocomplete="on">
<input type="hidden" name="client_id" value="${clientId?html}"><input type="hidden" name="grant_type" value="authorization_code">
<input type="hidden" name="TENANT-ID" value="${tenantId?c}"><input type="hidden" name="zenflow_csrf" value="${csrf?html}">
<label for="loginEmail" data-zh="邮箱" data-en="Email">邮箱</label><input id="loginEmail" type="email" name="username" autocomplete="username" required>
<label for="loginPassword" data-zh="密码" data-en="Password">密码</label><input id="loginPassword" type="password" name="password" autocomplete="current-password" required>
<#if error??><p class="error" role="alert" data-zh="登录失败，请检查邮箱和密码。" data-en="Sign-in failed. Check your email and password.">登录失败，请检查邮箱和密码。</p></#if>
<button class="primary" type="submit" data-zh="登录" data-en="Sign in">登录</button></form>
<div class="links"><button type="button" id="showRegister" data-zh="创建账号" data-en="Create account">创建账号</button><button type="button" id="showReset" data-zh="忘记密码" data-en="Forgot password">忘记密码</button></div></section>
<section id="emailPanel" class="hidden"><h1 id="emailTitle">创建账号</h1>
<label for="email" data-zh="邮箱" data-en="Email">邮箱</label><input id="email" type="email" autocomplete="email">
<p class="hint" id="emailHint">我们会将验证码发送到你的邮箱。</p><button class="primary" id="send" type="button" data-zh="发送验证码" data-en="Send code">发送验证码</button></section>
<section id="verifyPanel" class="hidden"><h1 data-zh="验证邮箱" data-en="Check your email">验证邮箱</h1>
<p class="hint" id="sentHint"></p><label for="code" data-zh="8 位验证码" data-en="8-digit code">8 位验证码</label><input id="code" inputmode="numeric" maxlength="8" autocomplete="one-time-code">
<button class="primary" id="verify" type="button" data-zh="继续" data-en="Continue">继续</button></section>
<section id="passwordPanel" class="hidden"><h1 id="passwordTitle">设置密码</h1>
<label for="newPassword" data-zh="新密码" data-en="New password">新密码</label><input id="newPassword" type="password" autocomplete="new-password" minlength="10">
<p class="hint" data-zh="至少 10 个字符，最多 72 个 UTF-8 字节。" data-en="At least 10 characters, at most 72 UTF-8 bytes.">至少 10 个字符，最多 72 个 UTF-8 字节。</p>
<button class="primary" id="complete" type="button" data-zh="完成" data-en="Finish">完成</button></section>
<p class="error" id="status" role="alert" aria-live="polite"></p><button id="back" type="button" class="hidden" data-zh="返回登录" data-en="Back to sign in">返回登录</button>
</main>
<script>
(() => {
  const base = '${authBase?js_string}';
  const csrf = '${csrf?html}';
  let lang = navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en', mode = 'REGISTER', challengeId = '', ticket = '';
  const $ = id => document.getElementById(id);
  const words = {zh:{register:'创建账号',reset:'找回密码',sent:'如果邮箱可用，验证码已发送。',done:'已完成，请用邮箱和密码登录。',error:'操作没有完成，请稍后重试。'},en:{register:'Create account',reset:'Reset password',sent:'If the email is eligible, a code has been sent.',done:'Done. Sign in with your email and password.',error:'Could not complete this step. Please try again.'}};
  function show(id){for(const panel of ['loginPanel','emailPanel','verifyPanel','passwordPanel']) $(panel).classList.toggle('hidden',panel!==id);$('back').classList.toggle('hidden',id==='loginPanel');$('status').textContent='';}
  function localize(){document.documentElement.lang=lang==='zh'?'zh-CN':'en';$('lang').textContent=lang==='zh'?'EN':'中文';document.querySelectorAll('[data-zh]').forEach(el=>el.textContent=el.dataset[lang]);$('emailTitle').textContent=words[lang][mode==='REGISTER'?'register':'reset'];$('passwordTitle').textContent=mode==='REGISTER'?(lang==='zh'?'设置密码':'Set password'):words[lang].reset;$('emailHint').textContent=lang==='zh'?'我们会将验证码发送到你的邮箱。':'We will send a verification code to your email.';}
  async function post(path,body){const response=await fetch(base+path,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-Zenflow-CSRF':csrf},body:JSON.stringify(body)});const result=await response.json();if(!response.ok)throw new Error(result.errorCode||'ERROR');return result;}
  async function action(button,callback){button.disabled=true;$('status').textContent='';try{await callback();}catch(error){$('status').textContent=error.message==='MAIL_UNAVAILABLE'?(lang==='zh'?'邮件服务暂不可用':'Email is temporarily unavailable'):words[lang].error;}finally{button.disabled=false;}}
  $('lang').onclick=()=>{lang=lang==='zh'?'en':'zh';localize();};
  $('showRegister').onclick=()=>{mode='REGISTER';localize();show('emailPanel');};
  $('showReset').onclick=()=>{mode='RESET';localize();show('emailPanel');};
  $('back').onclick=()=>show('loginPanel');
  $('send').onclick=()=>action($('send'),async()=>{const result=await post('/email/challenges',{email:$('email').value,purpose:mode,locale:lang==='zh'?'zh-CN':'en-US'});challengeId=result.challengeId;$('sentHint').textContent=words[lang].sent;show('verifyPanel');});
  $('verify').onclick=()=>action($('verify'),async()=>{const result=await post('/email/verifications',{challengeId,code:$('code').value});ticket=result.verificationTicket;show('passwordPanel');localize();});
  $('complete').onclick=()=>action($('complete'),async()=>{if(mode==='REGISTER')await post('/registrations',{verificationTicket:ticket,password:$('newPassword').value,locale:lang==='zh'?'zh-CN':'en-US'});else await post('/password-resets',{verificationTicket:ticket,newPassword:$('newPassword').value});show('loginPanel');$('status').className='success';$('status').textContent=words[lang].done;});
  localize();
})();
</script>
</body></html>
