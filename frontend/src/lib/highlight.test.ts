import { describe, expect, it } from "vitest";
import type { Board, HighlightSlot, HighlightSlotKind, PostCard } from "./api";
import { bentoImage, bentoLayout, slotKicker } from "./highlight";

const ORIGINAL = "https://i.postimg.cc/a/b.jpg";
const THUMB = "https://img.test/thumbs/5.webp";
const LARGE = "https://img.test/thumbs/5-1280.webp";

function card(board: Board, images: Partial<PostCard> = {}): PostCard {
  return {
    id: 5,
    board,
    title: "글",
    category: "KEYBOARD",
    subcategory: null,
    representativeImageUrl: ORIGINAL,
    representativeThumbUrl: THUMB,
    representativeLargeUrl: LARGE,
    summaryStatus: "DONE",
    summaryFirstLine: "요약 첫 줄",
    views: 100,
    replies: 1,
    statsCheckedAt: "2026-11-05T00:00:00Z",
    postedAt: "2026-11-01T00:00:00Z",
    estimatedPostedAt: null,
    lastPostAt: null,
    gbStartDate: null,
    gbEndDate: null,
    detailLoaded: true,
    ...images,
  };
}

const BOARD_OF: Record<HighlightSlotKind, Board> = {
  FEATURE: "GB",
  TALL: "IC",
  DEADLINE: "GB",
  IC_REPLIES: "IC",
  IC_VIEWS: "IC",
  GB_VIEWS: "GB",
};

function slot(kind: HighlightSlotKind, windowDays: number | null, rank: number | null, deadline = false): HighlightSlot {
  return { slot: kind, deadline, windowDays, rank, post: card(BOARD_OF[kind]) };
}

const NB = " ";

describe("slotKicker", () => {
  it("넓은 칸(큰 칸·세로 칸·마감 띠 대체)은 기간 앞에 최근을 붙인다", () => {
    expect(slotKicker(slot("FEATURE", 7, 1))).toEqual([`최근${NB}7일 새 공동구매`, `조회${NB}1위`]);
    expect(slotKicker(slot("TALL", 14, 1))).toEqual([`최근${NB}2주 새 IC`, `조회${NB}1위`]);
    expect(slotKicker(slot("DEADLINE", 14, 2))).toEqual([`최근${NB}2주 새 공동구매`, `조회${NB}2위`]);
  });

  it("작은 칸은 최근을 빼고, IC 댓글 칸은 댓글 순위", () => {
    expect(slotKicker(slot("IC_REPLIES", 14, 1))).toEqual(["2주 새 IC", `댓글${NB}1위`]);
    expect(slotKicker(slot("IC_VIEWS", 14, 2))).toEqual(["2주 새 IC", `조회${NB}2위`]);
    expect(slotKicker(slot("GB_VIEWS", 30, 1))).toEqual(["30일 새 공동구매", `조회${NB}1위`]);
  });

  it("마감 띠는 기간·순위 없이 한 토막", () => {
    expect(slotKicker(slot("DEADLINE", null, null, true))).toEqual(["곧 끝나는 공동구매"]);
  });

  it("넓힌 창은 그 일수로, 전체로 넓혔으면 전체 기간", () => {
    expect(slotKicker(slot("FEATURE", 30, 1))).toEqual([`최근${NB}30일 새 공동구매`, `조회${NB}1위`]);
    expect(slotKicker(slot("TALL", 90, 1))[0]).toBe(`최근${NB}90일 새 IC`);
    expect(slotKicker(slot("IC_VIEWS", 90, 3))[0]).toBe("90일 새 IC");
    expect(slotKicker(slot("FEATURE", null, 1))).toEqual(["전체 기간 새 공동구매", `조회${NB}1위`]);
    expect(slotKicker(slot("GB_VIEWS", null, 1))[0]).toBe("전체 기간 새 공동구매");
  });
});

describe("bentoLayout", () => {
  const all = [
    slot("FEATURE", 7, 1),
    slot("TALL", 14, 1),
    slot("DEADLINE", null, null, true),
    slot("IC_REPLIES", 14, 1),
    slot("IC_VIEWS", 14, 2),
    slot("GB_VIEWS", 30, 1),
  ];

  it("여섯 칸이 다 있으면 벤토", () => {
    expect(bentoLayout(all)).toBe("bento");
  });

  it("마감 띠가 사진 칸으로 바뀌어도 여섯 칸이면 벤토", () => {
    expect(bentoLayout([...all.slice(0, 2), slot("DEADLINE", 14, 2), ...all.slice(3)])).toBe("bento");
  });

  it("칸이 빠졌으면 남은 칸을 격자로", () => {
    expect(bentoLayout(all.slice(0, 5))).toBe("grid");
    expect(bentoLayout([all[1], all[3]])).toBe("grid");
    expect(bentoLayout([all[0]])).toBe("grid");
  });

  it("칸이 없으면 빈 상태", () => {
    expect(bentoLayout([])).toBe("empty");
  });
});

describe("bentoImage", () => {
  it("큰 칸은 1280 축소본을 먼저 읽고, 실패하면 원본", () => {
    expect(bentoImage(card("GB"), true)).toEqual({ src: LARGE, fallback: ORIGINAL });
  });

  it("큰 칸에 1280 축소본이 없으면 640 축소본", () => {
    expect(bentoImage(card("GB", { representativeLargeUrl: null }), true)).toEqual({ src: THUMB, fallback: ORIGINAL });
  });

  it("축소본이 하나도 없으면 원본만", () => {
    const post = card("GB", { representativeLargeUrl: null, representativeThumbUrl: null });
    expect(bentoImage(post, true)).toEqual({ src: ORIGINAL, fallback: null });
    expect(bentoImage(post, false)).toEqual({ src: ORIGINAL, fallback: null });
  });

  it("나머지 칸은 1280이 있어도 640 축소본만 본다", () => {
    expect(bentoImage(card("IC"), false)).toEqual({ src: THUMB, fallback: ORIGINAL });
    expect(bentoImage(card("IC", { representativeThumbUrl: null }), false)).toEqual({ src: ORIGINAL, fallback: null });
  });

  it("대표 이미지가 없으면 null", () => {
    const post = card("IC", { representativeImageUrl: null, representativeThumbUrl: null, representativeLargeUrl: null });
    expect(bentoImage(post, true)).toBeNull();
    expect(bentoImage(post, false)).toBeNull();
  });
});
