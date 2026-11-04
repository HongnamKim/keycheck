package com.keycheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** V5까지 만든 DB에 데이터를 넣은 뒤 V6을 적용한다. 앱이 쓰는 스키마와 섞이지 않게 따로 만든 스키마에서 한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MigrationV6Test {
  private static final String SCHEMA = "migration_v6_test";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void tearDown() {
    jdbcTemplate.execute("drop schema if exists " + SCHEMA + " cascade");
  }

  private void migrate(String target) {
    Flyway.configure()
        .dataSource(dataSource)
        .schemas(SCHEMA)
        .locations("classpath:db/migration")
        .target(target)
        .load()
        .migrate();
  }

  @Test
  @DisplayName("기존 글은 IC 게시판 글이 되고, IC 확보 상태는 그대로 둔 채 GB 확보 줄이 비어 있는 상태로 생긴다")
  void existingRowsBecomeIcAndGbBackfillIsAdded() {
    migrate("5");
    jdbcTemplate.update(
        "insert into " + SCHEMA + ".post (topic_id, title, author, category, stats_checked_at,"
            + " original_url, created_at, updated_at)"
            + " values (2000, '[IC] Board', 'maker', 'KEYBOARD', now(), 'https://geekhack.test', now(), now())");
    jdbcTemplate.update(
        "update " + SCHEMA + ".board_backfill set next_page = 3, floor_topic_id = 1500,"
            + " exhausted = false");

    migrate("6");

    assertThat(
            jdbcTemplate.queryForObject(
                "select board from " + SCHEMA + ".post where topic_id = 2000", String.class))
        .isEqualTo("IC");
    List<Map<String, Object>> backfills =
        jdbcTemplate.queryForList(
            "select board, next_page, floor_topic_id, exhausted from "
                + SCHEMA
                + ".board_backfill order by board");
    assertThat(backfills)
        .extracting(
            row -> row.get("board"),
            row -> row.get("next_page"),
            row -> row.get("floor_topic_id"),
            row -> row.get("exhausted"))
        .containsExactly(tuple("GB", 0, null, false), tuple("IC", 3, 1500L, false));
  }
}
