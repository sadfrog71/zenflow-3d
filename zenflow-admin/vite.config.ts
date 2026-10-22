import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({base:'/manage/',plugins:[vue()],server:{proxy:{'/api':{target:'https://invelleai.com',changeOrigin:true,secure:true}}}});
