const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../../../prototype'),out=path.resolve(__dirname,'../../src/features/zenflow');
const esbuild=require('../../../zenflow-admin/node_modules/esbuild');
const koi=fs.readFileSync(path.join(__dirname,'garden-koi.js'),'utf8');
const scene=fs.readFileSync(path.join(__dirname,'garden-scene.js'),'utf8')
 .replace("async function build(){",koi+'\nasync function build(){')
 .replace("ready=true;notifyParent('ready');","createInvelleKoi();ready=true;notifyParent('ready');")
 .replace("renderer.render(scene,camera);projectCushion();","if(!still)updateInvelleKoi(clock.getElapsedTime());renderer.render(scene,camera);projectCushion();")
 .replace("const embedded=sceneParams.get('embed')==='1';",'const embedded=true;')
 .replace(/const notifyParent=.*?;\n/, "const notifyParent=(type,data={})=>window.dispatchEvent(new CustomEvent('invelle-garden',{detail:{type,...data}}));\n")
 .replace('transition=null;requestFrame()}', 'requestFrame()}')
 .replace("loader.loadAsync('./assets/'+f)", "loader.loadAsync(window.InvelleGardenAssets?.[f]||'./assets/'+f)")
 .replace("function fail(error){notifyParent('failed');","function fail(error){notifyParent('failed',{reason:String(error?.message||error?.type||error)});")
 .replace('translate();build().catch(fail);', "window.InvelleGardenScene={sit,configure(data){lang=data.lang==='en'?'en':'zh';setStill(Boolean(data.reduce)||media.matches);translate();}};translate();build().catch(fail);");
const bundled=esbuild.buildSync({stdin:{contents:scene,resolveDir:__dirname,loader:'js'},bundle:true,write:false,format:'iife',minify:true,alias:{three:path.join(__dirname,'three.module.min.js')}}).outputFiles[0].text;
let sceneHtml=fs.readFileSync(path.join(root,'immersive/index.html'),'utf8').replace(/<link rel="stylesheet"[^>]*>/,()=>'<style>'+fs.readFileSync(path.join(root,'immersive/scene.css'),'utf8')+'</style>').replace(/<script type="importmap">[\s\S]*?<\/script>/,'').replace(/<script type="module"[^>]*><\/script>/,()=>'<script>'+bundled+'</script>');
const app=fs.readFileSync(path.join(root,'app.js'),'utf8').split('\n');
const presence=app.find(x=>x.startsWith('function presence()')).replace('immersive/index.html?','scene.html?');
const sound=app.filter(x=>/^function (soundControl|refreshSound|soundOptions|stopSound|replaceSound|alignBreathSound|chooseSound)\(|^async function toggleSound\(/.test(x)).join('\n').replace('Starts only when you choose. Pauses when you leave.','Wooden fish plays on entry. Turn it off anytime. Pauses when you leave.').replace('由你开启，离开休息页或切到后台时停止。','进入庭院时自动播放木鱼，可随时关闭；离开或切到后台停止。');
const css=['styles.css','serenity.css','mobile-refinement.css','experience.css','community.css'].map(x=>fs.readFileSync(path.join(root,x),'utf8')).join('\n');
const nativeMarkup=fs.readFileSync(path.join(root,'immersive/index.html'),'utf8').split('<body>')[1].split('</body>')[0].replace(/<script[\s\S]*?<\/script>/g,'');
const sceneCss=fs.readFileSync(path.join(root,'immersive/scene.css'),'utf8');
const controller=fs.readFileSync(path.join(__dirname,'garden-native.js'),'utf8');
const incenseCss=fs.readFileSync(path.join(__dirname,'garden-incense.css'),'utf8');
const incenseMarkup=fs.readFileSync(path.join(__dirname,'garden-incense.html'),'utf8');
const incenseScript=fs.readFileSync(path.join(__dirname,'garden-incense.js'),'utf8');
const clouds=fs.readFileSync(path.join(__dirname,'clouds.css'),'utf8');
const html='<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><style>'+sceneCss+'\n'+css+'\n'+clouds+'\n'+incenseCss+require('./heading-font.cjs')+'\nhtml,body,#app{height:100%;min-height:0;margin:0;padding:0;overflow:hidden}.presence-view.mobile-garden{height:100%;min-height:0;background:transparent}.garden-seated{height:100%}#dialog{max-width:calc(100% - 30px)}#native-scene{position:absolute;inset:0;z-index:0}#app{position:relative;z-index:1;pointer-events:none}.garden-frame{pointer-events:none;background:transparent!important}.presence-view button{pointer-events:auto}#dialog{pointer-events:auto}.garden-status{pointer-events:none}.garden-intro{top:46px!important}.mobile-garden .presence-dock{bottom:25px}.garden-drag-hint{bottom:85px}.invelle-clouds{opacity:.07;bottom:54%}.garden-top-shade{background:linear-gradient(rgba(18,31,24,.24),transparent)!important}.garden-intro,.garden-intro p,.garden-intro h1,.garden-intro span{color:#29463a!important;text-shadow:0 1px 12px #f9f2decc}.garden-incense{top:auto;bottom:30%;width:64px;height:146px}@media(min-width:761px){.garden-intro p,.garden-intro h1,.garden-intro span{color:#2c4237!important;text-shadow:0 1px 15px #f4f0e6cc}.garden-intro{top:19%!important}.garden-incense{bottom:12%}}</style></head><body class="presence-page embedded"><div id="native-scene">'+nativeMarkup+'</div><div class="invelle-clouds" aria-hidden="true"><i></i><i></i><i></i></div><main id="app"></main>'+incenseMarkup+'<dialog id="dialog"><div id="dialog-content"></div></dialog><script>'+fs.readFileSync(path.join(root,'meditation.js'),'utf8')+'\n'+fs.readFileSync(path.join(root,'ambience.js'),'utf8')+'\n'+incenseScript+'\n'+controller+'\n'+presence+'\n'+sound+'\nrender();\n'+bundled+'</script></body></html>';
const fingerprint=crypto.createHash('sha256').update(html).update(sceneHtml);
for(const name of fs.readdirSync(path.join(__dirname,'../../assets/zenflow/garden/assets')).filter(name=>/\.(png|jpg)$/.test(name)).sort())fingerprint.update(name).update(fs.readFileSync(path.join(__dirname,'../../assets/zenflow/garden/assets',name)));
const revision=fingerprint.digest('hex').slice(0,16);
fs.writeFileSync(path.join(out,'garden-source.json'),JSON.stringify({html,sceneHtml,revision})+'\n');
console.log('Built approved v0.8 garden:',revision);
