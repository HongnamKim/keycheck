package com.keycheck.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keycheck.summary.config.SummaryProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(OutputCaptureExtension.class)
class SummarySlotsTest {
  private final SummaryWorker worker = mock(SummaryWorker.class);

  /** 받은 칸을 모아 두기만 한다. 테스트가 직접 돌린다. */
  private final List<Runnable> submitted = new ArrayList<>();

  private final SummarySlots slots = slots(submitted::add);

  private SummarySlots slots(Executor executor) {
    return new SummarySlots(worker, new SummaryProperties(false, false, false, 300, 2), executor);
  }

  @Test
  @DisplayName("부를 때마다 빈 칸을 하나 연다")
  void opensOneSlotPerCall() {
    slots.openIfFree();

    assertThat(submitted).hasSize(1);
    assertThat(slots.openCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("동시 처리 수보다 많은 칸을 열지 않는다")
  void neverExceedsConcurrency() {
    slots.openIfFree();
    slots.openIfFree();
    slots.openIfFree();

    assertThat(submitted).hasSize(2);
    assertThat(slots.openCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("열린 칸은 대상이 있는 동안 이어서 요약하고, 없으면 닫힌다")
  void slotKeepsSummarizingWhileTargetsExist() {
    when(worker.summarizeNext(any(Runnable.class))).thenReturn(true, true, false);
    slots.openIfFree();

    submitted.get(0).run();

    verify(worker, times(3)).summarizeNext(any(Runnable.class));
    assertThat(slots.openCount()).isZero();
  }

  @Test
  @DisplayName("글을 집은 칸은 빈 칸이 있으면 하나를 더 연다")
  void claimingOpensAnotherSlot() {
    when(worker.summarizeNext(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return false;
            });
    slots.openIfFree();

    submitted.get(0).run();

    assertThat(submitted).hasSize(2);
    // 첫 칸은 닫혔고 새로 연 칸이 남았다
    assertThat(slots.openCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("칸 안에서 예외가 나도 밖으로 내지 않고 로그를 남기며, 칸 수가 돌아와 다음에 다시 연다")
  void exceptionClosesSlotAndRestoresCount(CapturedOutput output) {
    when(worker.summarizeNext(any(Runnable.class)))
        .thenThrow(new DataAccessResourceFailureException("DB 연결 끊김"));
    slots.openIfFree();

    assertThatCode(() -> submitted.get(0).run()).doesNotThrowAnyException();

    assertThat(slots.openCount()).isZero();
    assertThat(output.getAll()).contains("요약 칸이 예외로 닫혔다", "DB 연결 끊김");
    slots.openIfFree();
    assertThat(submitted).hasSize(2);
  }

  @Test
  @DisplayName("종료가 시작되면 열린 칸은 하던 요약만 마치고 닫히며, 새 칸을 열지 않는다")
  void stopsClaimingAfterContextClosed() {
    when(worker.summarizeNext(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              // 요약하는 동안 종료가 시작된다
              slots.onContextClosed();
              return true;
            })
        // 대기 글이 더 있어도 집지 않아야 한다(멈추지 않으면 두 번 불린다)
        .thenReturn(false);
    slots.openIfFree();

    submitted.get(0).run();

    verify(worker, times(1)).summarizeNext(any(Runnable.class));
    assertThat(slots.openCount()).isZero();
    slots.openIfFree();
    assertThat(submitted).hasSize(1);
  }

  @Test
  @DisplayName("스레드 풀이 칸을 받지 않으면 칸 수를 되돌린다")
  void rejectedSubmitRestoresCount() {
    SummarySlots rejecting =
        slots(
            task -> {
              throw new RejectedExecutionException("빈 스레드 없음");
            });

    assertThatCode(rejecting::openIfFree).doesNotThrowAnyException();

    assertThat(rejecting.openCount()).isZero();
  }
}
