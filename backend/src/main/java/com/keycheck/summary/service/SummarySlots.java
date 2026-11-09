package com.keycheck.summary.service;

import com.keycheck.summary.config.SummaryProperties;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * geekhack 글 요약 칸. 칸은 요약 전용 스레드 풀에서 돌며 대상이 없어지거나 쉬는 상태가 될 때까지 글을 이어서 요약한다. 동시에 여는 칸은
 * concurrency개까지다. 스케줄러가 주기마다 빈 칸을 하나 열고, 글을 집은 칸은 빈 칸이 남았으면 하나를 더 열어 밀린 글이 있으면 다음 주기를
 * 기다리지 않고 차오르게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "keycheck.summary.enabled", havingValue = "true")
public class SummarySlots {
  private final SummaryWorker worker;
  private final SummaryProperties properties;
  private final Executor executor;

  /** 열린 칸 수. 제출하기 전에 올리고, 제출이 거절되거나 칸이 닫히면 내린다 */
  private final AtomicInteger open = new AtomicInteger();

  /** 종료가 시작됐으면 true. 칸을 열지 않고 다음 글을 집지 않는다 */
  private volatile boolean stopping;

  public SummarySlots(
      SummaryWorker worker,
      SummaryProperties properties,
      @Qualifier("summaryExecutor") Executor executor) {
    this.worker = worker;
    this.properties = properties;
    this.executor = executor;
  }

  /** 빈 칸이 있으면 하나를 연다 */
  public void openIfFree() {
    if (stopping) {
      return;
    }
    int current;
    do {
      current = open.get();
      if (current >= properties.getConcurrency()) {
        return;
      }
    } while (!open.compareAndSet(current, current + 1));
    try {
      executor.execute(this::runSlot);
    } catch (RejectedExecutionException e) {
      open.decrementAndGet();
      // 막 닫힌 칸의 스레드가 아직 돌아오지 않았거나 종료 중이다. 다음 주기에 다시 연다
      log.warn("요약 칸을 열지 못했다: {}", e.toString());
    }
  }

  /**
   * 종료가 시작되면(컨텍스트를 닫기 시작할 때) 새 칸을 열지 않고, 열린 칸은 하던 요약만 마치고 닫히게 한다. 버려질 Claude 호출을 새로 시작하지
   * 않게 하려는 것이다. 하던 요약은 기다리지 않는다(SummaryExecutorConfig).
   */
  @EventListener(ContextClosedEvent.class)
  public void onContextClosed() {
    stopping = true;
  }

  /** 테스트용 */
  int openCount() {
    return open.get();
  }

  /** 예외가 나도 칸 수를 돌려놓아 칸이 새지 않게 한다. 일시 장애면 다음 주기에 다시 열린다 */
  private void runSlot() {
    try {
      while (!stopping && worker.summarizeNext(this::openIfFree)) {
        // 대상이 없거나 쉬는 상태가 되거나 종료가 시작될 때까지 이어서 요약한다
      }
    } catch (RuntimeException e) {
      log.error("요약 칸이 예외로 닫혔다", e);
    } finally {
      open.decrementAndGet();
    }
  }
}
