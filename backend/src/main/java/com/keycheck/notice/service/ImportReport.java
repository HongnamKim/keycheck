package com.keycheck.notice.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 지난 공지 가져오기 결과. aborted면 저장소 장애로 중간에 멈춘 것이며, 다시 실행하면 이어서 끝낸다. */
@Getter
@RequiredArgsConstructor
public class ImportReport {
  private final int imported;
  private final int skipped;
  private final int attachmentsStored;
  private final int attachmentsFailed;
  private final boolean aborted;

  @Override
  public String toString() {
    return "imported=" + imported + ", skipped=" + skipped + ", attachmentsStored="
        + attachmentsStored + ", attachmentsFailed=" + attachmentsFailed + ", aborted=" + aborted;
  }
}
