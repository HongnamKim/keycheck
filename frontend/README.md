# 키체크 프론트엔드

Next.js 16(App Router, TypeScript) 화면. 서비스 소개, 실행 방법, 환경변수는 저장소 루트의 [README](../README.md)를 본다.

```bash
npm ci
npm run dev        # http://localhost:3000 (백엔드는 NEXT_PUBLIC_API_BASE_URL, 기본 http://localhost:8080)
npm test && npm run lint && npm run build
```

- Next.js 코드를 고치기 전에는 `AGENTS.md` 안내대로 `node_modules/next/dist/docs/`를 먼저 본다.
- 파비콘·홈 화면 아이콘은 `public/`에 있고, 원본은 `docs/design/keycheck-icon.svg`다.
