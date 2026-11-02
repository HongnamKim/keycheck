package com.keycheck.post.dto;

import java.util.List;

public record PostPageResponse(List<PostCardResponse> items, int page, int totalPages) {}
