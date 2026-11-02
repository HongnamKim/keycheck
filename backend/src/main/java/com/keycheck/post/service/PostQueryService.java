package com.keycheck.post.service;

import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.dto.CategoryFilter;
import com.keycheck.post.dto.PostCardResponse;
import com.keycheck.post.dto.PostDetailResponse;
import com.keycheck.post.dto.PostPageResponse;
import com.keycheck.post.dto.PostSort;
import com.keycheck.post.repository.PostImageRepository;
import com.keycheck.post.repository.PostRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryService {
  private static final int PAGE_SIZE = 20;

  private final PostRepository postRepository;
  private final PostImageRepository postImageRepository;

  public PostPageResponse getPosts(CategoryFilter filter, PostSort sort, int page) {
    Pageable pageable = PageRequest.of(page, PAGE_SIZE, sort.getSort());
    Page<Post> posts =
        filter.getCategory() != null
            ? postRepository.findByHiddenFalseAndCategory(filter.getCategory(), pageable)
            : postRepository.findByHiddenFalse(pageable);

    Map<Long, String> imageUrls = representativeImageUrls(posts.getContent());
    List<PostCardResponse> items =
        posts.getContent().stream()
            .map(post -> PostCardResponse.from(post, imageUrls.get(post.getRepresentativeImageId())))
            .toList();
    return new PostPageResponse(items, page, posts.getTotalPages());
  }

  public PostDetailResponse getPost(Long id) {
    Post post =
        postRepository
            .findById(id)
            .filter(found -> !found.isHidden())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String imageUrl = representativeImageUrls(List.of(post)).get(post.getRepresentativeImageId());
    return PostDetailResponse.from(post, imageUrl);
  }

  private Map<Long, String> representativeImageUrls(List<Post> posts) {
    List<Long> ids =
        posts.stream().map(Post::getRepresentativeImageId).filter(Objects::nonNull).toList();
    if (ids.isEmpty()) {
      return Collections.emptyMap();
    }
    return postImageRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(PostImage::getId, PostImage::getUrl));
  }
}
