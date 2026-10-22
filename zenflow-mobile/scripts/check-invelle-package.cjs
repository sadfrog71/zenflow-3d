const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const app=process.argv[2];if(!app)throw Error('Pass the signed Invelle.app path');
const data=fs.readFileSync(path.join(app,'main.jsbundle'));
const has=value=>data.includes(Buffer.from(value))||data.includes(Buffer.from(value,'utf16le'));
const env=Object.fromEntries(fs.readFileSync(path.resolve(__dirname,'../.env.invelle'),'utf8').split('\n').filter(x=>x&&!x.startsWith('#')&&x.includes('=')).map(x=>{const i=x.indexOf('=');return[x.slice(0,i),x.slice(i+1).trim().replace(/^['"]|['"]$/g,'')]}));
for(const key of ['EXPO_PUBLIC_ZENFLOW_API_URL','EXPO_PUBLIC_ZENFLOW_OAUTH_CLIENT_ID','EXPO_PUBLIC_ZENFLOW_GOOGLE_IOS_CLIENT_ID','EXPO_PUBLIC_ZENFLOW_GOOGLE_WEB_CLIENT_ID'])assert(env[key]&&has(env[key]),'Missing packaged '+key);
for(const marker of ['gardenSoundStarted','syncSlogans','invelle-drift','InvelleIncense','data-incense-stick','incense-ceramic'])assert(has(marker),'Missing '+marker);
const revision=require('../src/features/zenflow/garden-source.json').revision;assert(has(revision),'Packaged garden revision is stale');
console.log('PASS packaged API/OAuth/Google configuration and garden/home behaviour');
