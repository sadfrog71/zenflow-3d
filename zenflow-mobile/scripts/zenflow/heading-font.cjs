const fs = require('node:fs');
const path = require('node:path');
const font = fs.readFileSync(path.join(__dirname, '../../assets/fonts/invelle-heading.ttf')).toString('base64');
module.exports = `@font-face{font-family:InvelleHeading;src:url(data:font/ttf;base64,${font}) format('truetype');font-weight:400;font-display:swap}html[lang="zh-CN"] .presence-heading h1,html[lang="zh-CN"] #title,html[lang="zh-CN"] .garden-intro h1{font-family:InvelleHeading,"Songti SC",serif}`;
