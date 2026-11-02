/**
 * 조회수 표기 (설계 5장)
 * - 1,000 미만: 그대로 (999)
 * - 1만 미만: 앞 두 자리만 남기고 내림 (3,400+)
 * - 1만 이상: 만 단위 소수 첫째 자리까지 내림 (1.2만+, 8만+)
 */
export function formatViews(views: number): string {
  if (views < 1_000) return String(views);
  if (views < 10_000) return `${(Math.floor(views / 100) * 100).toLocaleString("en-US")}+`;

  const tenthsOfMan = Math.floor(views / 1_000); // 만 단위 × 10
  const whole = Math.floor(tenthsOfMan / 10).toLocaleString("en-US");
  const decimal = tenthsOfMan % 10;
  return decimal === 0 ? `${whole}만+` : `${whole}.${decimal}만+`;
}

/** 조회수를 마지막으로 확인한 시각을 상대 시간으로 표기 (예: 3시간 전 확인) */
export function formatCheckedAt(checkedAt: Date, now: Date = new Date()): string {
  const minutes = Math.floor((now.getTime() - checkedAt.getTime()) / 60_000);
  if (minutes < 1) return "방금 확인";
  if (minutes < 60) return `${minutes}분 전 확인`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전 확인`;

  return `${Math.floor(hours / 24)}일 전 확인`;
}
