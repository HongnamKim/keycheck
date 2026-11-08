import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    // server-only 패키지는 따로 설치하지 않고 Next에 들어 있는 빈 모듈로 테스트에서 푼다
    alias: { "server-only": "next/dist/compiled/server-only/empty.js" },
  },
});
