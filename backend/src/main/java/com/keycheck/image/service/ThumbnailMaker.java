package com.keycheck.image.service;

import com.luciad.imageio.webp.WebPReadParam;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * 원본 이미지를 가로 최대 폭의 WebP 축소본으로 만든다 (설계 3.1·3.2).
 *
 * <p>원본보다 키우지 않고 비율을 지킨다. 움직이는 GIF는 첫 장면, 투명 배경은 그대로 둔다. JPEG의 EXIF 방향은 브라우저가 원본을 보여주는
 * 방향대로 바로 세운다.
 */
@Component
public class ThumbnailMaker {
  /** 카드·하단 목록·갤러리 칸 */
  public static final int SMALL_WIDTH = 640;

  /** 상세 맨 위 대표 이미지 */
  public static final int LARGE_WIDTH = 1280;

  /** WebP 손실 압축 품질 */
  private static final float QUALITY = 0.8f;

  /** WebP가 담을 수 있는 가로·세로 최대 픽셀 */
  private static final int MAX_WEBP_SIZE = 16383;

  /** 원본의 가로·세로 최대 픽셀. 줄여 읽어도 아주 넓은 PNG는 한 줄 버퍼가 원본 폭만큼 커진다. */
  private static final int MAX_ORIGINAL_SIDE = 30000;

  /** 원본의 최대 픽셀 수(12000×9000은 들어간다). 점진적 JPEG은 MAX_JPEG_COEFFICIENT_BYTES로 더 낮게 막는다. */
  private static final long MAX_ORIGINAL_PIXELS = 150_000_000;

  /**
   * 여러 스캔으로 나눈 JPEG(점진적 JPEG 등)을 펼칠 때 디코더(libjpeg)가 잡는 DCT 계수 버퍼의 상한. 이 버퍼는 줄여 읽어도 원본 크기만큼
   * 힙 밖에 잡혀 -Xmx로 막히지 않고 OutOfMemoryError도 나지 않는다(메모리가 모자라면 운영체제가 프로세스를 죽인다). 4:2:0이면 픽셀당
   * 3바이트라 약 4500만 픽셀, 4:4:4면 6바이트라 약 2200만 픽셀까지 들어간다.
   */
  private static final long MAX_JPEG_COEFFICIENT_BYTES = 128L * 1024 * 1024;

  /**
   * 축소본을 만드는 원본 형식(ImageReader의 형식 이름, 소문자). 브라우저가 보여주는 형식만 받는다. TIFF 안의 JPEG처럼 위 상한들을 피해
   * 디코더를 부르는 길을 막는다.
   */
  private static final Set<String> FORMATS = Set.of("jpeg", "png", "gif", "webp");

  /** 줄여 읽은 장면이 이 바이트 안에 들 때까지 배율을 키운다. 16비트 PNG처럼 픽셀당 바이트가 큰 장면도 바이트로 잰다. */
  private static final long MAX_DECODED_BYTES = 64L * 1024 * 1024;

  /**
   * 목표 폭 밑으로는 줄여 읽지 않으므로(다시 키우면 흐려진다) 세로로 아주 긴 이미지는 MAX_DECODED_BYTES보다 크게 읽는다. 이
   * 바이트까지만 받고, 넘으면 펼치지 않고 실패로 센다.
   *
   * <p>make() 한 번이 힙에 잡는 양은 많아야 이 장면과 축소본 장면(1280×16383×4 ≈ 84MB), 또는 축소본과 인코딩 사본 두 벌 정도다.
   */
  private static final long MAX_TALL_DECODED_BYTES = 128L * 1024 * 1024;

  /** EXIF 방향을 찾으려고 보는 JPEG 앞부분. EXIF는 영상 데이터 앞, 보통 첫 한두 세그먼트에 있다. */
  private static final int EXIF_SEARCH_BYTES = 64 * 1024;

  /** EXIF 방향을 찾으며 건너뛸 세그먼트 수 상한 */
  private static final int MAX_EXIF_SEARCH_SEGMENTS = 32;

  public byte[] make(byte[] original, int maxWidth) {
    ImageWriter writer = webpWriter();
    try {
      return encode(writer, shrink(original, maxWidth));
    } catch (UnreadableImageException e) {
      throw e;
    } catch (IOException | RuntimeException | OutOfMemoryError e) {
      // 깨진 파일은 디코더가 런타임 예외로도 실패한다. 힙이 작은 곳에서 긴 이미지의 큰 배열을 못 잡아도
      // 이 이미지 탓이다(잡다 만 장면은 버려지므로 이어서 일할 수 있다).
      // 이미지 탓이므로 실패로 센다(놓치면 작업이 같은 이미지를 계속 다시 고른다).
      throw new UnreadableImageException("축소본을 만들 수 없는 이미지", e);
    } finally {
      writer.dispose();
    }
  }

  /**
   * 가로가 maxWidth의 2배 이상 남는 가장 큰 정수 배율. 아주 큰 이미지를 줄여 읽어 메모리를 지킨다.
   *
   * <p>세로로 아주 긴 이미지는 그래도 크게 읽히므로, 줄여 읽은 장면이 MAX_DECODED_BYTES 안에 들 때까지 더 줄인다. 다만 가로가
   * maxWidth보다 작아지게는 줄이지 않는다.
   */
  static int subsampling(int width, int height, int maxWidth, int bytesPerPixel) {
    int factor = Math.max(1, width / (maxWidth * 2));
    int widest = Math.max(1, width / maxWidth);
    while (factor < widest
        && decodedBytes(width, height, factor, bytesPerPixel) > MAX_DECODED_BYTES) {
      factor++;
    }
    return factor;
  }

  /** 배율 factor로 줄여 읽은 장면의 바이트 */
  private static long decodedBytes(int width, int height, int factor, int bytesPerPixel) {
    return (long) Math.ceilDiv(width, factor) * Math.ceilDiv(height, factor) * bytesPerPixel;
  }

  /** WebP 인코더가 없으면 설정 문제라 이미지 실패로 세지 않는다. */
  private ImageWriter webpWriter() {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
    if (!writers.hasNext()) {
      throw new IllegalStateException("WebP 인코더가 없다");
    }
    return writers.next();
  }

  private BufferedImage shrink(byte[] original, int maxWidth) throws IOException {
    // 기본 createImageInputStream은 임시 파일을 쓰므로 메모리 스트림을 쓴다(이미 바이트로 받았다).
    try (ImageInputStream input =
        new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new UnreadableImageException("이미지 형식을 알 수 없다");
      }
      ImageReader reader = readers.next();
      try {
        if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
          throw new UnreadableImageException("축소본을 만들지 않는 형식: " + reader.getFormatName());
        }
        reader.setInput(input, true, true);
        int orientation = orientation(original);
        // 5~8은 눕혀 저장한 사진이라 보이는 가로세로가 저장된 것과 반대다.
        boolean turned = orientation >= 5;
        int width = turned ? reader.getHeight(0) : reader.getWidth(0);
        int height = turned ? reader.getWidth(0) : reader.getHeight(0);
        if (width > MAX_ORIGINAL_SIDE
            || height > MAX_ORIGINAL_SIDE
            || (long) width * height > MAX_ORIGINAL_PIXELS) {
          // 줄여 읽어도 디코더가 원본 크기만큼 잡는 메모리가 있으므로 펼치기 전에 실패로 센다.
          throw new UnreadableImageException("너무 큰 이미지");
        }
        int targetWidth = Math.min(width, maxWidth);
        int targetHeight = Math.max(1, (int) Math.round((double) height * targetWidth / width));
        if (targetHeight > MAX_WEBP_SIZE) {
          // 어차피 WebP로 만들 수 없으므로 원본을 펼치기 전에 실패로 센다.
          throw new UnreadableImageException("WebP로 만들 수 없을 만큼 긴 이미지");
        }
        ImageReadParam param = reader.getDefaultReadParam();
        if (param instanceof WebPReadParam webp) {
          // 무손실 WebP는 줄여 읽어도 libwebp가 원본 전체를 픽셀당 4바이트로 펼치므로 원본 크기로 상한을 본다.
          // 손실 WebP의 알파 청크도 원본 크기로 푼다(6000×6000에서 약 180MB를 더 잡았다). 같은 상한을 본다.
          // 알파 없는 손실 WebP는 줄을 차례로 풀며 줄이므로 상한이 없다.
          if (decodesAtFullSize(original) && (long) width * height * 4 > MAX_DECODED_BYTES) {
            throw new UnreadableImageException("너무 큰 무손실·투명 WebP 이미지");
          }
          // WebP 읽기는 서브샘플링을 무시한다. 대신 libwebp가 처음부터 목표 크기로 줄여 읽는다.
          if (targetWidth < width) {
            webp.setUseScaling(true);
            webp.setScaledWidth(targetWidth);
            webp.setScaledHeight(targetHeight);
          }
        } else {
          if (jpegCoefficientBytes(original) > MAX_JPEG_COEFFICIENT_BYTES) {
            // 펼치는 순간 힙 밖에 잡히므로 펼치기 전에 실패로 센다.
            throw new UnreadableImageException("여러 스캔으로 나눈(점진적) JPEG이라 펼치면 메모리를 너무 많이 쓴다");
          }
          int bytesPerPixel = bytesPerPixel(reader);
          int factor = subsampling(width, height, maxWidth, bytesPerPixel);
          if (decodedBytes(width, height, factor, bytesPerPixel) > MAX_TALL_DECODED_BYTES) {
            throw new UnreadableImageException("목표 폭으로 줄여 읽어도 너무 큰 이미지");
          }
          param.setSourceSubsampling(factor, factor, 0, 0);
        }
        BufferedImage frame = reader.read(0, param);
        return resize(frame, orientation, targetWidth, targetHeight);
      } finally {
        reader.dispose();
      }
    }
  }

  private BufferedImage resize(BufferedImage frame, int orientation, int width, int height) {
    int type =
        frame.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    BufferedImage resized = new BufferedImage(width, height, type);
    boolean turned = orientation >= 5;
    int shownWidth = turned ? frame.getHeight() : frame.getWidth();
    int shownHeight = turned ? frame.getWidth() : frame.getHeight();
    AffineTransform transform =
        AffineTransform.getScaleInstance(
            (double) width / shownWidth, (double) height / shownHeight);
    transform.concatenate(upright(orientation, frame.getWidth(), frame.getHeight()));
    Graphics2D graphics = resized.createGraphics();
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.drawImage(frame, transform, null);
    graphics.dispose();
    return resized;
  }

  /** 저장된 장면(가로 w, 세로 h)의 좌표를 EXIF 방향대로 바로 세운 좌표로 옮긴다. */
  private static AffineTransform upright(int orientation, int w, int h) {
    return switch (orientation) {
      case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0); // 좌우 뒤집기
      case 3 -> new AffineTransform(-1, 0, 0, -1, w, h); // 180도
      case 4 -> new AffineTransform(1, 0, 0, -1, 0, h); // 상하 뒤집기
      case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // 대각선 뒤집기
      case 6 -> new AffineTransform(0, 1, -1, 0, h, 0); // 시계 방향 90도
      case 7 -> new AffineTransform(0, -1, -1, 0, h, w); // 반대 대각선 뒤집기
      case 8 -> new AffineTransform(0, -1, 1, 0, 0, w); // 반시계 방향 90도
      default -> new AffineTransform();
    };
  }

  /** 읽기가 펼칠 장면의 픽셀당 바이트. 8비트 RGB는 3, 16비트 RGBA PNG는 8이다. */
  private static int bytesPerPixel(ImageReader reader) throws IOException {
    // 펼칠 형식을 따로 정하지 않으면 읽기는 이 목록의 첫 형식으로 펼친다.
    SampleModel sample = reader.getImageTypes(0).next().getSampleModel();
    int bits = sample.getNumDataElements() * DataBuffer.getDataTypeSize(sample.getDataType());
    return Math.max(1, bits / 8);
  }

  /**
   * 줄여 읽어도 libwebp가 원본 크기로 푸는 WebP인지 본다. 무손실("VP8L")이나 손실 형식의 알파("ALPH") 청크가 있으면 그렇다. 단순
   * 형식은 첫 청크가 "VP8L"이고, 확장 형식("VP8X")은 뒤따르는 청크 중에 이들이 있다.
   */
  static boolean decodesAtFullSize(byte[] webp) {
    ByteBuffer riff = ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN);
    long position = 12; // "RIFF", 파일 크기, "WEBP" 다음이 첫 청크
    while (position + 8 <= webp.length) {
      int at = (int) position;
      String chunk = new String(webp, at, 4, StandardCharsets.US_ASCII);
      if (chunk.equals("VP8L") || chunk.equals("ALPH")) {
        return true;
      }
      long size = Integer.toUnsignedLong(riff.getInt(at + 4));
      position += 8 + size + (size & 1); // 청크는 짝수 바이트로 맞춘다
    }
    return false;
  }

  /**
   * 여러 스캔으로 나눈 JPEG(점진적 JPEG, 성분마다 스캔을 나눈 순차 JPEG)을 펼칠 때 디코더(libjpeg)가 원본 전체 크기로 잡는 DCT 계수
   * 버퍼의 바이트. 성분마다 8×8 블록 수 × 계수 64개 × 2바이트다. JPEG가 아니거나 한 스캔짜리면 0, 머리를 읽지 못하면 Long.MAX_VALUE.
   *
   * <p>디코더처럼 마커 앞의 다른 바이트와 0xFF 채움, FF 00을 건너뛰며 첫 SOF와 첫 SOS까지만 본다. 첫 스캔이 모든 성분을 담지 않거나
   * 점진적 JPEG이면 디코더가 여러 스캔으로 보고 계수를 모두 모아 둔다.
   */
  static long jpegCoefficientBytes(byte[] jpeg) {
    if (jpeg.length < 2 || jpeg[0] != (byte) 0xFF || jpeg[1] != (byte) 0xD8) {
      return 0;
    }
    ByteBuffer data = ByteBuffer.wrap(jpeg);
    data.position(2);
    long bytes = -1; // 첫 SOF로 잰 계수 버퍼
    boolean progressive = false;
    int components = 0;
    try {
      while (true) {
        int marker = nextJpegMarker(data);
        if (marker == 0xDA) { // SOS
          if (bytes < 0) {
            return Long.MAX_VALUE;
          }
          data.getShort(); // 길이
          int scanComponents = data.get() & 0xFF;
          return progressive || scanComponents < components ? bytes : 0;
        }
        if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) {
          continue; // 길이 칸이 없는 마커(TEM, RSTn, SOI)
        }
        if (marker == 0xD9) {
          return Long.MAX_VALUE; // 스캔 없이 끝났다
        }
        int length = data.getShort() & 0xFFFF; // 길이 칸 2바이트를 포함한다
        if (length < 2) {
          return Long.MAX_VALUE;
        }
        int next = data.position() + length - 2;
        // SOF0~SOF15. 그 사이의 DHT(C4), JPG(C8), DAC(CC)는 빼고
        boolean sof =
            marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
        if (sof && bytes < 0) {
          progressive = marker == 0xC2 || marker == 0xC6 || marker == 0xCA || marker == 0xCE;
          data.get(); // 정밀도
          int height = data.getShort() & 0xFFFF;
          int width = data.getShort() & 0xFFFF;
          components = data.get() & 0xFF;
          int[] horizontal = new int[components];
          int[] vertical = new int[components];
          for (int i = 0; i < components; i++) {
            data.get(); // 성분 번호
            int sampling = data.get() & 0xFF;
            horizontal[i] = sampling >> 4;
            vertical[i] = sampling & 0x0F;
            data.get(); // 양자화 표 번호
          }
          bytes = coefficientBytes(width, height, horizontal, vertical);
        }
        data.position(next);
      }
    } catch (RuntimeException e) {
      return Long.MAX_VALUE; // 머리가 잘렸거나 길이 칸이 파일 밖을 가리킨다
    }
  }

  /** 다음 마커 코드. 디코더처럼 앞의 다른 바이트, 0xFF 채움, 마커가 아닌 FF 00을 건너뛴다. */
  private static int nextJpegMarker(ByteBuffer data) {
    while (true) {
      while (data.get() != (byte) 0xFF) {
        // 마커 앞의 다른 바이트
      }
      int code;
      do {
        code = data.get() & 0xFF;
      } while (code == 0xFF);
      if (code != 0) {
        return code;
      }
    }
  }

  /** 성분마다 libjpeg가 잡는 블록 수(가로·세로를 샘플링 배수로 올림) × 128바이트의 합. 샘플링 값이 잘못됐으면 Long.MAX_VALUE. */
  private static long coefficientBytes(int width, int height, int[] horizontal, int[] vertical) {
    int maxHorizontal = 1;
    int maxVertical = 1;
    for (int i = 0; i < horizontal.length; i++) {
      if (horizontal[i] < 1 || horizontal[i] > 4 || vertical[i] < 1 || vertical[i] > 4) {
        return Long.MAX_VALUE;
      }
      maxHorizontal = Math.max(maxHorizontal, horizontal[i]);
      maxVertical = Math.max(maxVertical, vertical[i]);
    }
    long bytes = 0;
    for (int i = 0; i < horizontal.length; i++) {
      long wide = blocks(width, horizontal[i], maxHorizontal);
      long high = blocks(height, vertical[i], maxVertical);
      bytes += wide * high * 64 * 2;
    }
    return bytes;
  }

  /** 한 방향의 블록 수. 샘플링 비율로 줄인 길이를 8로 나눠 올리고, 다시 샘플링 배수로 올린다. */
  private static long blocks(int length, int sampling, int maxSampling) {
    long blocks = Math.ceilDiv((long) length * sampling, maxSampling * 8L);
    return Math.ceilDiv(blocks, sampling) * sampling;
  }

  /**
   * JPEG의 EXIF 방향(1~8). JPEG가 아니거나 방향이 없거나 읽을 수 없으면 1(그대로).
   *
   * <p>계획에 없던 보정이지만 남긴다. 휴대폰 사진은 눕혀 저장하고 방향만 적어 두는 일이 많고, 브라우저는 원본을 이 방향대로 세워 보여주므로
   * 축소본만 누워 보이면 안 된다. 깨진 머리에서도 앞부분(EXIF_SEARCH_BYTES)과 세그먼트 수 안에서 끝나고 예외 없이 1을 돌려준다.
   */
  static int orientation(byte[] image) {
    try {
      ByteBuffer jpeg = ByteBuffer.wrap(image, 0, Math.min(image.length, EXIF_SEARCH_BYTES));
      if (jpeg.getShort() != (short) 0xFFD8) {
        return 1;
      }
      for (int i = 0; i < MAX_EXIF_SEARCH_SEGMENTS; i++) {
        int marker = jpeg.getShort() & 0xFFFF;
        if (marker == 0xFFDA || (marker & 0xFF00) != 0xFF00) {
          return 1; // 영상 데이터가 시작되기 전까지 EXIF가 없었다
        }
        int length = jpeg.getShort() & 0xFFFF; // 길이 칸 2바이트를 포함한다
        if (length < 2) {
          return 1;
        }
        int next = jpeg.position() + length - 2;
        if (marker == 0xFFE1 && jpeg.getInt() == 0x45786966 && jpeg.getShort() == 0) { // "Exif\0\0"
          int end = Math.min(next, jpeg.limit());
          return exifOrientation(jpeg.slice(jpeg.position(), end - jpeg.position()));
        }
        jpeg.position(next);
      }
      return 1;
    } catch (RuntimeException e) {
      return 1;
    }
  }

  /** TIFF 머리부터 시작하는 EXIF에서 첫 IFD의 방향 태그(0x0112)를 읽는다. */
  private static int exifOrientation(ByteBuffer tiff) {
    tiff.order(tiff.getShort(0) == 0x4949 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    int ifd = tiff.getInt(4);
    int count = tiff.getShort(ifd) & 0xFFFF;
    for (int i = 0; i < count; i++) {
      int entry = ifd + 2 + i * 12;
      if ((tiff.getShort(entry) & 0xFFFF) == 0x0112) {
        int value = tiff.getShort(entry + 8) & 0xFFFF;
        return value >= 1 && value <= 8 ? value : 1;
      }
    }
    return 1;
  }

  private byte[] encode(ImageWriter writer, BufferedImage image) throws IOException {
    ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionType("Lossy");
    param.setCompressionQuality(QUALITY);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream output = new MemoryCacheImageOutputStream(out)) {
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), param);
    }
    return out.toByteArray();
  }
}
