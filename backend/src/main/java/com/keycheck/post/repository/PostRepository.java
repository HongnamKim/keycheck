package com.keycheck.post.repository;

import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
  Optional<Post> findByTopicId(Long topicId);

  Page<Post> findByHiddenFalse(Pageable pageable);

  Page<Post> findByHiddenFalseAndCategory(Category category, Pageable pageable);
}
