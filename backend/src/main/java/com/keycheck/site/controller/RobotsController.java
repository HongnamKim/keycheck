package com.keycheck.site.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 백엔드 도메인(api.keycheck.kr)의 robots.txt. 검색 엔진이 API 응답을 긁거나 색인하지 않게 전체를 막는다. 화면 도메인의 robots.txt는
 * 프론트가 따로 준다. 서버 설정(Caddy)이 아니라 여기에 두어 이미지 배포만으로 반영된다.
 */
@RestController
public class RobotsController {
  private static final String ROBOTS = "User-agent: *\nDisallow: /\n";

  /** 형식을 produces 대신 응답에 정해, Accept에 text/plain이 없는 수집기도 406이 아니라 이 내용을 받는다(4xx면 제한 없음으로 본다) */
  @GetMapping("/robots.txt")
  public ResponseEntity<String> robots() {
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(ROBOTS);
  }
}
