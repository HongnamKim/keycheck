import { describe, expect, it } from "vitest";
import { imageSources, shownImageSrc } from "./image";

describe("imageSources", () => {
  it("축소본이 있으면 축소본을 먼저 읽고, 실패하면 원본으로 바꾼다", () => {
    expect(imageSources("https://img.test/thumbs/5.webp", "https://i.postimg.cc/a/b.jpg")).toEqual({
      src: "https://img.test/thumbs/5.webp",
      fallback: "https://i.postimg.cc/a/b.jpg",
    });
  });

  it("축소본이 없으면 원본을 읽고 바꿀 주소는 없다", () => {
    expect(imageSources(null, "https://i.postimg.cc/a/b.jpg")).toEqual({
      src: "https://i.postimg.cc/a/b.jpg",
      fallback: null,
    });
  });

  it("축소본 주소가 빈 문자열이면 없는 것으로 보고 원본을 읽는다", () => {
    expect(imageSources("", "https://i.postimg.cc/a/b.jpg")).toEqual({
      src: "https://i.postimg.cc/a/b.jpg",
      fallback: null,
    });
  });
});

describe("shownImageSrc", () => {
  const thumb = "https://img.test/thumbs/5-1280.webp";
  const original = "https://i.postimg.cc/a/b.jpg";

  it("아직 실패한 주소가 없으면 src를 읽는다", () => {
    expect(shownImageSrc(thumb, original, null)).toBe(thumb);
  });

  it("src를 읽지 못하면 fallback으로 바꾼다", () => {
    expect(shownImageSrc(thumb, original, thumb)).toBe(original);
  });

  it("fallback이 없으면 실패해도 src 그대로다", () => {
    expect(shownImageSrc(original, null, original)).toBe(original);
  });

  it("fallback이 빈 문자열이면 없는 것으로 보고 실패해도 src 그대로다", () => {
    expect(shownImageSrc(original, "", original)).toBe(original);
  });

  it("원본이 실패한 뒤 새로 만든 축소본이 들어오면(새로고침) 축소본부터 다시 읽는다", () => {
    // 처음: 축소본 없음 → src = 원본, 원본이 실패해 실패 주소 = 원본. 새로고침 뒤 src = 축소본, fallback = 원본
    expect(shownImageSrc(thumb, original, original)).toBe(thumb);
  });
});
