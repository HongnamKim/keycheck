package com.keycheck.image;

import com.keycheck.crawl.RawImage;
import java.util.List;

public interface RepresentativeImageSelector {
  Selection select(List<RawImage> images);
}
