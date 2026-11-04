import BoardColumn from "@/components/BoardColumn";
import styles from "./page.module.css";

/** 메인. 짧은 소개와 IC·공동구매 최근 글 두 칸 */
export default function Home() {
  return (
    <div className={styles.home}>
      <section className={styles.hero} aria-labelledby="hero-title">
        <div className={styles.intro}>
          <h1 id="hero-title" className={styles.heroTitle}>
            키보드·키캡 IC와 공동구매를
            <br />
            한국어로 먼저 훑어보세요
          </h1>
          <p className={styles.lead}>
            geekhack의 인터레스트 체크와 공동구매 글을 모아 핵심만 한국어로 요약해요. 대표 이미지와 스펙을 보고, 관심 가는
            글은 원문으로 바로 넘어가면 돼요.
          </p>
        </div>
        <dl className={styles.facts}>
          <div>
            <dt>요약</dt>
            <dd>한국어 3~4줄</dd>
          </div>
          <div>
            <dt>확인 주기</dt>
            <dd>30분마다</dd>
          </div>
          <div>
            <dt>원문</dt>
            <dd>geekhack</dd>
          </div>
        </dl>
      </section>

      <div className={styles.columns}>
        <BoardColumn board="ic" />
        <BoardColumn board="gb" />
      </div>
    </div>
  );
}
