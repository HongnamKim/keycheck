package com.keycheck.image.service;

import com.keycheck.crawl.dto.RawImage;
import com.keycheck.image.dto.Selection;
import java.util.List;

public interface RepresentativeImageSelector {
  Selection select(List<RawImage> images);
}
