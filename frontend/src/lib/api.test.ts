import { afterEach, describe, expect, it, vi } from "vitest";
import {
  API_BASE_URL,
  apiRequestInit,
  getHighlights,
  getNeighbors,
  getPost,
  getPosts,
  getSitemapPostIds,
  getStats,
} from "./api";

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("apiRequestInit", () => {
  it("서버에서 토큰이 있으면 X-Keycheck-Server 헤더를 붙인다", () => {
    const init = apiRequestInit(true, "secret");

    expect(new Headers(init.headers).get("X-Keycheck-Server")).toBe("secret");
  });

  it("브라우저에서는 토큰이 있어도 헤더를 붙이지 않는다", () => {
    const init = apiRequestInit(false, "secret");

    expect(new Headers(init.headers).has("X-Keycheck-Server")).toBe(false);
  });

  it("서버라도 토큰이 없으면 헤더를 붙이지 않는다", () => {
    expect(new Headers(apiRequestInit(true, undefined).headers).has("X-Keycheck-Server")).toBe(false);
    expect(new Headers(apiRequestInit(true, "").headers).has("X-Keycheck-Server")).toBe(false);
  });

  it("넘긴 signal이 없어도 8초 시간 제한을 건다", () => {
    const timeout = new AbortController();
    const spy = vi.spyOn(AbortSignal, "timeout").mockReturnValue(timeout.signal);

    const init = apiRequestInit(false, undefined);

    expect(spy).toHaveBeenCalledWith(8000);
    expect(init.signal?.aborted).toBe(false);
    timeout.abort();
    expect(init.signal?.aborted).toBe(true);
  });

  it("넘긴 signal과 시간 제한을 합친다: 넘긴 signal을 끊으면 결과도 끊긴다", () => {
    const caller = new AbortController();

    const init = apiRequestInit(true, "secret", caller.signal);

    expect(init.signal?.aborted).toBe(false);
    caller.abort();
    expect(init.signal?.aborted).toBe(true);
  });

  it("넘긴 signal과 시간 제한을 합친다: 시간 제한이 끝나도 결과가 끊긴다", () => {
    const timeout = new AbortController();
    vi.spyOn(AbortSignal, "timeout").mockReturnValue(timeout.signal);
    const caller = new AbortController();

    const init = apiRequestInit(false, undefined, caller.signal);

    timeout.abort();
    expect(init.signal?.aborted).toBe(true);
    expect(caller.signal.aborted).toBe(false);
  });

  it("AbortSignal.any가 없는 브라우저에서는 넘긴 signal을 그대로 쓴다", () => {
    vi.stubGlobal("AbortSignal", { timeout: AbortSignal.timeout.bind(AbortSignal) });
    const caller = new AbortController();

    const init = apiRequestInit(false, undefined, caller.signal);

    expect(init.signal).toBe(caller.signal);
  });
});

// vitest는 window가 없어 화면 서버 경로로 부른다. React cache는 RSC 밖에서 그대로 통과하므로 getHighlights도 매번 fetch한다
describe("백엔드 호출 함수", () => {
  it.each([
    ["getPosts", () => getPosts("ic", "all", "recent")],
    ["getHighlights", () => getHighlights()],
    ["getPost", () => getPost(1)],
    ["getStats", () => getStats(1)],
    ["getNeighbors", () => getNeighbors(1, { board: "ic", category: "all", sort: "recent" }, 5)],
    ["getSitemapPostIds", () => getSitemapPostIds()],
  ])("%s는 시간 제한과 X-Keycheck-Server 헤더를 붙여 백엔드를 부른다", async (_, call) => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response("[]")));
    vi.stubGlobal("fetch", fetchMock);
    vi.stubEnv("KEYCHECK_SERVER_TOKEN", "t");

    await call();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url.startsWith(`${API_BASE_URL}/api/`)).toBe(true);
    expect(init.signal).toBeInstanceOf(AbortSignal);
    expect(new Headers(init.headers).get("X-Keycheck-Server")).toBe("t");
  });

  it("getPost는 틀 글을 채우는 백엔드를 기다리도록 15초, 다른 호출은 8초 시간 제한을 건다", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response("{}"))));
    const spy = vi.spyOn(AbortSignal, "timeout");

    await getPost(1);
    expect(spy).toHaveBeenLastCalledWith(15000);

    await getStats(1);
    expect(spy).toHaveBeenLastCalledWith(8000);
  });

  it("getPost는 fill=false면 쿼리에 실어 보내고 기본 8초 시간 제한을 건다", async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response("{}")));
    vi.stubGlobal("fetch", fetchMock);
    const spy = vi.spyOn(AbortSignal, "timeout");

    await getPost(7, undefined, { fill: false });

    expect(fetchMock.mock.calls[0]).toEqual([`${API_BASE_URL}/api/posts/7?fill=false`, expect.anything()]);
    expect(spy).toHaveBeenLastCalledWith(8000);
  });

  it("getPost는 fill을 따로 주지 않거나 true면 쿼리 없이 부른다", async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response("{}")));
    vi.stubGlobal("fetch", fetchMock);

    await getPost(7);
    await getPost(7, undefined, { fill: true });

    expect(fetchMock.mock.calls.map((call) => (call as unknown as [string])[0])).toEqual([
      `${API_BASE_URL}/api/posts/7`,
      `${API_BASE_URL}/api/posts/7`,
    ]);
  });

  it("getPost는 404면 null을 돌려준다", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response("", { status: 404 }))));

    await expect(getPost(7, undefined, { fill: false })).resolves.toBeNull();
  });

  it("getSitemapPostIds는 응답의 글 ID만 순서대로 돌려준다", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(Response.json([{ id: 30 }, { id: 12 }]))));

    await expect(getSitemapPostIds()).resolves.toEqual([30, 12]);
  });

  it("getSitemapPostIds는 백엔드가 실패하면 예외를 던진다", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response("", { status: 503 }))));

    await expect(getSitemapPostIds()).rejects.toThrow("503");
  });
});
