// ============================================================
// Vite 配置
// - 开发环境: /api 与 /uploads 代理到 VITE_PROXY_TARGET(默认 8080)
// - 生产构建: 第三方库单独分包,避免单文件过大影响首屏
// ============================================================
import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxyTarget = env.VITE_PROXY_TARGET || "http://localhost:8080";

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    server: {
      port: 5173,
      host: true,
      proxy: {
        "/api": {
          target: proxyTarget,
          changeOrigin: true,
        },
        "/uploads": {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: "dist",
      sourcemap: false,
      chunkSizeWarningLimit: 800,
      rollupOptions: {
        output: {
          manualChunks: {
            // 基础框架与状态管理
            "vendor-core": ["vue", "vue-router", "pinia"],
            // UI 组件库单独成包,便于浏览器长期缓存
            "vendor-ui": ["element-plus", "@element-plus/icons-vue"],
          },
        },
      },
    },
  };
});
