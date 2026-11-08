package com.keycheck.image.service;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ThumbnailMakerTest {
  private final ThumbnailMaker maker = new ThumbnailMaker();

  private BufferedImage filled(int width, int height, int type, Color color) {
    BufferedImage image = new BufferedImage(width, height, type);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(color);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();
    return image;
  }

  private byte[] png(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private byte[] png(int width, int height) throws IOException {
    return png(filled(width, height, BufferedImage.TYPE_INT_RGB, Color.ORANGE));
  }

  /** compressionType은 "Lossy"(단순 손실 형식, 첫 청크 "VP8 ") 또는 "Lossless"(첫 청크 "VP8L"). */
  private byte[] webp(int width, int height, String compressionType) throws IOException {
    return webp(filled(width, height, BufferedImage.TYPE_INT_RGB, Color.ORANGE), compressionType);
  }

  private byte[] webp(BufferedImage image, String compressionType) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
    ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionType(compressionType);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(stream);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  private byte[] animatedGif(Color... frames) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(stream);
      writer.prepareWriteSequence(null);
      for (Color color : frames) {
        BufferedImage frame = filled(100, 100, BufferedImage.TYPE_INT_RGB, color);
        writer.writeToSequence(new IIOImage(frame, null, null), null);
      }
      writer.endWriteSequence();
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  /**
   * 왼쪽 절반은 빨강, 오른쪽 절반은 파랑인 400×200 JPEG에 EXIF 방향(APP1)을 넣는다. 휴대폰 사진처럼 눕혀 저장한 모양이다.
   *
   * <p>afterJfif면 ImageIO가 쓴 JFIF(APP0) 뒤에, 아니면 SOI 바로 뒤에 넣는다.
   */
  private byte[] jpegWithOrientation(ByteOrder order, int orientation, boolean afterJfif)
      throws IOException {
    BufferedImage stored = filled(400, 200, BufferedImage.TYPE_INT_RGB, Color.BLUE);
    Graphics2D graphics = stored.createGraphics();
    graphics.setColor(Color.RED);
    graphics.fillRect(0, 0, 200, 200);
    graphics.dispose();
    ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
    ImageIO.write(stored, "jpg", jpeg);
    byte[] body = jpeg.toByteArray();

    byte mark = (byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M');
    ByteBuffer tiff = ByteBuffer.allocate(26).order(order);
    tiff.put(mark).put(mark).putShort((short) 42).putInt(8); // TIFF 머리, 첫 IFD 위치
    tiff.putShort((short) 1); // 항목 1개
    tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1); // 방향, SHORT, 1개
    tiff.putShort((short) orientation).putShort((short) 0);
    tiff.putInt(0); // 다음 IFD 없음
    ByteBuffer app1 = ByteBuffer.allocate(4 + 6 + 26);
    app1.putShort((short) 0xFFE1).putShort((short) (2 + 6 + 26));
    app1.put("Exif\0\0".getBytes(US_ASCII)).put(tiff.array());

    int at = 2;
    if (afterJfif) {
      assertThat(body[3]).isEqualTo((byte) 0xE0); // SOI 다음이 JFIF APP0
      at = 4 + (((body[4] & 0xFF) << 8) | (body[5] & 0xFF));
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(body, 0, at);
    out.write(app1.array());
    out.write(body, at, body.length - at);
    return out.toByteArray();
  }

  /** 머리(IHDR)만 있고 픽셀 데이터가 없는 PNG. 펼치면 실패하므로, 펼치기 전에 크기로 거절하는지 볼 때 쓴다. */
  private byte[] pngHeaderOnly(int width, int height, int bitDepth, int colorType) {
    ByteBuffer ihdr = ByteBuffer.allocate(4 + 13);
    ihdr.put("IHDR".getBytes(US_ASCII)).putInt(width).putInt(height);
    ihdr.put((byte) bitDepth).put((byte) colorType).put((byte) 0).put((byte) 0).put((byte) 0);
    CRC32 crc = new CRC32();
    crc.update(ihdr.array());
    ByteBuffer png = ByteBuffer.allocate(8 + 4 + ihdr.capacity() + 4 + 12);
    png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
    png.putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
    png.putInt(0).put("IEND".getBytes(US_ASCII)).putInt(0xAE426082);
    return png.array();
  }

  /** 4:2:0(Y만 가로·세로 2배로 샘플링). 성분마다 가로·세로 샘플링(0xHV)이다. */
  private static final int[] YCC_420 = {0x22, 0x11, 0x11};

  /** 4:4:4 */
  private static final int[] YCC_444 = {0x11, 0x11, 0x11};

  /**
   * 머리(SOFn, 첫 SOS)만 있고 표와 영상 데이터가 없는 JPEG. 펼치면 실패하므로, 펼치기 전에 크기로 거절하는지 볼 때 쓴다.
   * scanComponents는 첫 스캔이 담는 성분 수다.
   */
  private byte[] jpegHeaderOnly(
      int sof, int width, int height, int[] sampling, int scanComponents) {
    ByteBuffer jpeg = ByteBuffer.allocate(20 + 3 * sampling.length + 2 * scanComponents);
    jpeg.putShort((short) 0xFFD8);
    jpeg.put((byte) 0xFF).put((byte) sof).putShort((short) (8 + 3 * sampling.length));
    jpeg.put((byte) 8).putShort((short) height).putShort((short) width);
    jpeg.put((byte) sampling.length);
    for (int i = 0; i < sampling.length; i++) {
      jpeg.put((byte) (i + 1)).put((byte) sampling[i]).put((byte) 0);
    }
    jpeg.putShort((short) 0xFFDA).putShort((short) (6 + 2 * scanComponents));
    jpeg.put((byte) scanComponents);
    for (int i = 0; i < scanComponents; i++) {
      jpeg.put((byte) (i + 1)).put((byte) 0);
    }
    jpeg.put((byte) 0).put((byte) 63).put((byte) 0);
    return jpeg.array();
  }

  private byte[] jpeg(BufferedImage image, boolean progressive) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ImageWriteParam param = writer.getDefaultWriteParam();
    if (progressive) {
      param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(stream);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  private BufferedImage read(byte[] bytes) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(bytes));
  }

  /**
   * "OOM!"으로 시작하는 바이트만 맡는 가짜 형식. 크기(100×100)는 알려 주고, 펼치려 하면 힙이 모자란 것처럼 실패한다. 받는 형식만 펼치므로
   * 형식 이름은 png로 둔다(다른 PNG는 "OOM!"으로 시작하지 않아 맡지 않는다).
   */
  private static class OutOfMemoryReaderSpi extends ImageReaderSpi {
    OutOfMemoryReaderSpi() {
      vendorName = "keycheck";
      version = "1";
      names = new String[] {"png"};
      inputTypes = new Class<?>[] {ImageInputStream.class};
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
      byte[] magic = new byte[4];
      return ((ImageInputStream) source).read(magic) == 4
          && Arrays.equals(magic, "OOM!".getBytes(US_ASCII));
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
      return new ImageReader(this) {
        @Override
        public int getNumImages(boolean allowSearch) {
          return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
          return 100;
        }

        @Override
        public int getHeight(int imageIndex) {
          return 100;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
          return List.of(
                  ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB))
              .iterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
          return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
          return null;
        }

        @Override
        public BufferedImage read(int imageIndex, ImageReadParam param) {
          throw new OutOfMemoryError("Java heap space");
        }
      };
    }

    @Override
    public String getDescription(Locale locale) {
      return "펼치면 힙이 모자란 가짜 형식";
    }
  }

  /** 이 스레드가 일하는 동안 힙에 잡은 바이트. 원본 전체를 펼치지 않고 줄여 읽는지 본다. */
  private long allocatedDuring(Runnable work) {
    com.sun.management.ThreadMXBean threads =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long before = threads.getCurrentThreadAllocatedBytes();
    work.run();
    return threads.getCurrentThreadAllocatedBytes() - before;
  }

  @Test
  @DisplayName("가로를 최대 폭으로 줄이고 비율을 지켜 WebP로 만든다")
  void shrinksToMaxWidthKeepingRatio() throws IOException {
    byte[] webp = maker.make(png(2000, 1000), ThumbnailMaker.SMALL_WIDTH);

    BufferedImage thumb = read(webp);
    assertThat(thumb.getWidth()).isEqualTo(640);
    assertThat(thumb.getHeight()).isEqualTo(320);
    assertThat(new String(webp, 0, 4, US_ASCII)).isEqualTo("RIFF");
    assertThat(new String(webp, 8, 4, US_ASCII)).isEqualTo("WEBP");
    // 손실 압축(단순 형식의 첫 청크 "VP8 "). 무손실("VP8L")이면 사진 축소본이 여러 배 커진다.
    assertThat(new String(webp, 12, 4, US_ASCII)).isEqualTo("VP8 ");
  }

  @Test
  @DisplayName("원본이 최대 폭보다 작으면 키우지 않는다")
  void doesNotEnlargeSmallImage() throws IOException {
    BufferedImage thumb = read(maker.make(png(300, 200), ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(300);
    assertThat(thumb.getHeight()).isEqualTo(200);
  }

  @Test
  @DisplayName("대표 이미지용 큰 축소본은 가로 1280px로 줄인다")
  void largeWidth() throws IOException {
    BufferedImage thumb = read(maker.make(png(3000, 1500), ThumbnailMaker.LARGE_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(1280);
    assertThat(thumb.getHeight()).isEqualTo(640);
  }

  @Test
  @DisplayName("줄여 읽은(배율 2) 원본도 원본 비율대로 최대 폭에 맞춘다")
  void shrinksSubsampledOriginal() throws IOException {
    BufferedImage thumb = read(maker.make(png(2600, 1300), ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(640);
    assertThat(thumb.getHeight()).isEqualTo(320);
  }

  @Test
  @DisplayName("크기는 작아도 픽셀이 아주 큰 PNG는 줄여 읽어 원본 크기만큼 메모리를 쓰지 않는다")
  void readsHugePngSubsampled() throws IOException {
    // 1비트 흑백이라 PNG는 수 KB다. 그대로 펼쳐 줄이면 픽셀당 4바이트로 바뀌어 100MB를 넘는다.
    byte[] original = png(new BufferedImage(6000, 4500, BufferedImage.TYPE_BYTE_BINARY));
    byte[][] webp = new byte[1][];

    long allocated =
        allocatedDuring(() -> webp[0] = maker.make(original, ThumbnailMaker.SMALL_WIDTH));

    assertThat(allocated).isLessThan(48L * 1024 * 1024);
    BufferedImage thumb = read(webp[0]);
    assertThat(thumb.getWidth()).isEqualTo(640);
    assertThat(thumb.getHeight()).isEqualTo(480);
  }

  @Test
  @DisplayName("움직이는 GIF는 첫 장면으로 만든다")
  void firstFrameOfAnimatedGif() throws IOException {
    byte[] gif = animatedGif(Color.RED, Color.BLUE);

    BufferedImage thumb = read(maker.make(gif, ThumbnailMaker.SMALL_WIDTH));

    Color center = new Color(thumb.getRGB(thumb.getWidth() / 2, thumb.getHeight() / 2));
    assertThat(center.getRed()).isGreaterThan(center.getBlue());
  }

  @Test
  @DisplayName("투명 배경은 투명하게 둔다")
  void keepsTransparency() throws IOException {
    BufferedImage original = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = original.createGraphics();
    graphics.setColor(Color.GREEN);
    graphics.fillRect(100, 100, 200, 100);
    graphics.dispose();

    BufferedImage thumb = read(maker.make(png(original), ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getRGB(0, 0) >>> 24).isZero();
    // 투명하지 않은 그림(초록 사각형)은 그대로 남는다. 원본이 최대 폭보다 작아 좌표가 같다.
    int inside = thumb.getRGB(200, 150);
    Color color = new Color(inside);
    assertThat(inside >>> 24).isGreaterThan(250);
    assertThat(color.getGreen()).isGreaterThan(color.getRed());
    assertThat(color.getGreen()).isGreaterThan(color.getBlue());
  }

  @Test
  @DisplayName("WebP 원본도 읽어 줄인다")
  void readsWebpOriginal() throws IOException {
    byte[] original = maker.make(png(2000, 1000), ThumbnailMaker.LARGE_WIDTH);

    BufferedImage thumb = read(maker.make(original, ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(640);
    assertThat(thumb.getHeight()).isEqualTo(320);
  }

  @Test
  @DisplayName("큰 WebP 원본은 원본 크기로 펼치지 않고 처음부터 목표 크기로 읽는다")
  void readsLargeWebpAtTargetSize() throws IOException {
    // 원본 크기로 펼치면 int[4000×2000]만 32MB다.
    byte[] original = webp(4000, 2000, "Lossy");
    byte[][] webp = new byte[1][];

    long allocated =
        allocatedDuring(() -> webp[0] = maker.make(original, ThumbnailMaker.SMALL_WIDTH));

    assertThat(allocated).isLessThan(16L * 1024 * 1024);
    BufferedImage thumb = read(webp[0]);
    assertThat(thumb.getWidth()).isEqualTo(640);
    assertThat(thumb.getHeight()).isEqualTo(320);
  }

  @Test
  @DisplayName("펼칠 상한(64MB)보다 큰 무손실 WebP 원본은 읽기 전에 읽을 수 없는 이미지로 본다")
  void tooLargeWebpThrows() throws IOException {
    // 4100×4100×4바이트 ≈ 67.2MB
    byte[] original = webp(4100, 4100, "Lossless");

    assertThatThrownBy(() -> maker.make(original, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasNoCause();
  }

  @Test
  @DisplayName("같은 크기라도 알파 없는 손실 WebP 원본은 거절하지 않고 줄여 읽어 축소본을 만든다")
  void readsLargeLossyWebp() throws IOException {
    byte[] original = webp(4100, 4100, "Lossy");

    BufferedImage thumb = read(maker.make(original, ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(640);
    assertThat(thumb.getHeight()).isEqualTo(640);
  }

  @Test
  @DisplayName("같은 크기의 알파가 있는 손실 WebP 원본은 알파를 원본 크기로 푸므로 읽기 전에 읽을 수 없는 이미지로 본다")
  void tooLargeLossyWebpWithAlphaThrows() throws IOException {
    byte[] original =
        webp(new BufferedImage(4100, 4100, BufferedImage.TYPE_INT_ARGB), "Lossy"); // VP8X, ALPH, VP8

    assertThatThrownBy(() -> maker.make(original, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasNoCause();
  }

  @Test
  @DisplayName("원본 크기로 푸는 WebP는 단순 형식이든 확장 형식(VP8X) 뒤 청크든 VP8L이나 ALPH 청크로 알아본다")
  void detectsFullSizeWebp() throws IOException {
    BufferedImage translucent = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    byte[] lossyWithAlpha = webp(translucent, "Lossy");
    // 확장 형식: VP8X(10바이트) → ICCP(3바이트 + 짝수 맞춤 1바이트) → VP8L
    ByteBuffer extended = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN);
    extended.put("RIFF".getBytes(US_ASCII)).putInt(42).put("WEBP".getBytes(US_ASCII));
    extended.put("VP8X".getBytes(US_ASCII)).putInt(10).put(new byte[10]);
    extended.put("ICCP".getBytes(US_ASCII)).putInt(3).put(new byte[4]);
    extended.put("VP8L".getBytes(US_ASCII)).putInt(0);
    // 청크 크기 칸이 깨져 파일 밖을 가리킨다
    byte[] broken = extended.array().clone();
    ByteBuffer.wrap(broken).order(ByteOrder.LITTLE_ENDIAN).putInt(16, -1);

    assertThat(new String(lossyWithAlpha, 12, 4, US_ASCII)).isEqualTo("VP8X");
    assertThat(ThumbnailMaker.decodesAtFullSize(lossyWithAlpha)).isTrue();
    assertThat(ThumbnailMaker.decodesAtFullSize(webp(16, 16, "Lossy"))).isFalse();
    assertThat(ThumbnailMaker.decodesAtFullSize(webp(16, 16, "Lossless"))).isTrue();
    assertThat(ThumbnailMaker.decodesAtFullSize(extended.array())).isTrue();
    assertThat(ThumbnailMaker.decodesAtFullSize(broken)).isFalse();
  }

  @Test
  @DisplayName("원본 크기를 디코드 없이 읽는다")
  void readsOriginalSize() throws IOException {
    ImageSize size = maker.originalSize(png(3000, 2000));

    assertThat(size.getWidth()).isEqualTo(3000);
    assertThat(size.getHeight()).isEqualTo(2000);
  }

  @Test
  @DisplayName("EXIF 방향이 눕힌 사진이면 보이는 폭·높이로 준다")
  void originalSizeFollowsExifOrientation() throws IOException {
    ImageSize turned = maker.originalSize(jpegWithOrientation(ByteOrder.BIG_ENDIAN, 6, false));
    ImageSize upright = maker.originalSize(jpegWithOrientation(ByteOrder.BIG_ENDIAN, 1, false));

    assertThat(turned.getWidth()).isEqualTo(200);
    assertThat(turned.getHeight()).isEqualTo(400);
    assertThat(upright.getWidth()).isEqualTo(400);
    assertThat(upright.getHeight()).isEqualTo(200);
  }

  @Test
  @DisplayName("읽을 수 없는 원본의 크기는 UnreadableImageException")
  void originalSizeOfGarbageFails() {
    assertThatThrownBy(() -> maker.originalSize(new byte[] {1, 2, 3}))
        .isInstanceOf(UnreadableImageException.class);
  }

  @Test
  @DisplayName("JPEG의 EXIF 방향대로 바로 세운다(시계 방향 90도, 빅 엔디언)")
  void followsExifOrientation() throws IOException {
    byte[] original = jpegWithOrientation(ByteOrder.BIG_ENDIAN, 6, false);

    BufferedImage thumb = read(maker.make(original, ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(200);
    assertThat(thumb.getHeight()).isEqualTo(400);
    Color top = new Color(thumb.getRGB(100, 50));
    Color bottom = new Color(thumb.getRGB(100, 350));
    assertThat(top.getRed()).isGreaterThan(top.getBlue());
    assertThat(bottom.getBlue()).isGreaterThan(bottom.getRed());
  }

  @Test
  @DisplayName("EXIF(APP1)가 JFIF(APP0) 뒤에 있어도 방향대로 바로 세운다")
  void followsExifOrientationAfterJfif() throws IOException {
    byte[] original = jpegWithOrientation(ByteOrder.BIG_ENDIAN, 6, true);

    BufferedImage thumb = read(maker.make(original, ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(200);
    assertThat(thumb.getHeight()).isEqualTo(400);
    Color top = new Color(thumb.getRGB(100, 50));
    Color bottom = new Color(thumb.getRGB(100, 350));
    assertThat(top.getRed()).isGreaterThan(top.getBlue());
    assertThat(bottom.getBlue()).isGreaterThan(bottom.getRed());
  }

  @Test
  @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  @DisplayName("세그먼트 길이 칸이 0인 깨진 JPEG나 잘린 JPEG도 멈추지 않고 읽을 수 없는 이미지로 본다")
  void brokenJpegSegmentDoesNotHang() throws IOException {
    byte[] zeroLength = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    byte[] truncated = Arrays.copyOf(jpegWithOrientation(ByteOrder.BIG_ENDIAN, 6, true), 30);

    assertThatThrownBy(() -> maker.make(zeroLength, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class);
    assertThatThrownBy(() -> maker.make(truncated, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class);
  }

  @Test
  @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  @DisplayName("EXIF 방향 읽기는 깨진 머리에서도 예외 없이 바로 1(그대로)을 돌려준다")
  void orientationOfBrokenJpegIsOne() throws IOException {
    byte[] jfifThenExif = jpegWithOrientation(ByteOrder.BIG_ENDIAN, 6, true);
    // 길이 2짜리 빈 세그먼트만 길게 이어진다(세그먼트 수 상한에서 멈춘다)
    ByteBuffer emptySegments = ByteBuffer.allocate(2 + 4 * 10_000);
    emptySegments.putShort((short) 0xFFD8);
    while (emptySegments.hasRemaining()) {
      emptySegments.putShort((short) 0xFFE0).putShort((short) 2);
    }
    byte[] zeroLength = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    byte[] oneLength = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 1};
    // 세그먼트 길이가 앞부분(64KB) 밖을 가리킨다
    byte[] pointsOutside = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, (byte) 0xFF, 0};

    assertThat(ThumbnailMaker.orientation(jfifThenExif)).isEqualTo(6);
    assertThat(ThumbnailMaker.orientation(zeroLength)).isEqualTo(1);
    assertThat(ThumbnailMaker.orientation(oneLength)).isEqualTo(1);
    // SOI(2) + JFIF(18) + APP1 머리(4) + "Exif\0\0"(6) 다음 TIFF의 18~19번째 바이트가 방향 값이다.
    assertThat(ThumbnailMaker.orientation(Arrays.copyOf(jfifThenExif, 50))).isEqualTo(6);
    for (int length = 0; length < 50; length++) {
      assertThat(ThumbnailMaker.orientation(Arrays.copyOf(jfifThenExif, length)))
          .as("앞 %d바이트만 남은 JPEG", length)
          .isEqualTo(1);
    }
    assertThat(ThumbnailMaker.orientation(emptySegments.array())).isEqualTo(1);
    assertThat(ThumbnailMaker.orientation(pointsOutside)).isEqualTo(1);
  }

  @Test
  @DisplayName("JPEG의 EXIF 방향대로 바로 세운다(반시계 방향 90도, 리틀 엔디언)")
  void followsLittleEndianExifOrientation() throws IOException {
    byte[] original = jpegWithOrientation(ByteOrder.LITTLE_ENDIAN, 8, false);

    BufferedImage thumb = read(maker.make(original, ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(200);
    assertThat(thumb.getHeight()).isEqualTo(400);
    Color top = new Color(thumb.getRGB(100, 50));
    Color bottom = new Color(thumb.getRGB(100, 350));
    assertThat(top.getBlue()).isGreaterThan(top.getRed());
    assertThat(bottom.getRed()).isGreaterThan(bottom.getBlue());
  }

  @Test
  @DisplayName("이미지가 아니면 읽을 수 없는 이미지로 본다")
  void unreadableThrows() {
    assertThatThrownBy(() -> maker.make("<html>".getBytes(UTF_8), ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class);
  }

  @Test
  @DisplayName("펼치다 힙이 모자라면 읽을 수 없는 이미지로 본다(실패로 세어 같은 이미지를 계속 고르지 않는다)")
  void outOfMemoryCountsAsUnreadable() {
    OutOfMemoryReaderSpi spi = new OutOfMemoryReaderSpi();
    IIORegistry.getDefaultInstance().registerServiceProvider(spi);
    try {
      assertThatThrownBy(() -> maker.make("OOM!".getBytes(US_ASCII), ThumbnailMaker.SMALL_WIDTH))
          .isInstanceOf(UnreadableImageException.class)
          .hasCauseInstanceOf(OutOfMemoryError.class);
    } finally {
      IIORegistry.getDefaultInstance().deregisterServiceProvider(spi);
    }
  }

  @Test
  @DisplayName("WebP로 만들 수 없는 크기(세로 16383px 초과)면 원본을 펼치기 전에 읽을 수 없는 이미지로 본다")
  void tooTallForWebpThrows() {
    assertThatThrownBy(() -> maker.make(png(300, 17000), ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasNoCause();
  }

  @Test
  @DisplayName("원본이 1억 5000만 픽셀이나 가로 30000px를 넘으면 펼치기 전에 읽을 수 없는 이미지로 본다")
  void tooLargeOriginalThrows() throws IOException {
    // 1비트 흑백이라 PNG는 작다.
    byte[] manyPixels = png(new BufferedImage(12500, 12001, BufferedImage.TYPE_BYTE_BINARY));
    byte[] wide = png(new BufferedImage(30001, 1, BufferedImage.TYPE_BYTE_BINARY));

    assertThatThrownBy(() -> maker.make(manyPixels, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasNoCause();
    assertThatThrownBy(() -> maker.make(wide, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasNoCause();
  }

  @Test
  @DisplayName("여러 스캔으로 나눈(점진적) JPEG은 힙 밖에 잡는 계수 버퍼가 128MB를 넘으면 펼치기 전에 읽을 수 없는 이미지로 본다")
  void tooLargeProgressiveJpegThrows() {
    // 12000×12000 4:2:0 점진적 JPEG은 계수 버퍼가 약 432MB다(실제 파일은 1MB 남짓이라 받기 상한에 걸리지 않는다).
    byte[] progressive = jpegHeaderOnly(0xC2, 12000, 12000, YCC_420, 1);
    // 순차 JPEG이라도 첫 스캔이 성분 하나만 담으면 여러 스캔이라 계수를 모두 모아 둔다.
    byte[] componentScans = jpegHeaderOnly(0xC0, 12000, 12000, YCC_420, 1);
    // 같은 크기라도 한 스캔짜리 순차 JPEG은 줄을 차례로 풀므로 거절하지 않고 읽다가(표와 데이터가 없어) 실패한다.
    byte[] baseline = jpegHeaderOnly(0xC0, 12000, 12000, YCC_420, 3);
    // 계수 버퍼가 약 72MB인 점진적 JPEG(6000×4000 4:2:0)도 거절하지 않고 읽다가 실패한다.
    byte[] smallProgressive = jpegHeaderOnly(0xC2, 6000, 4000, YCC_420, 1);

    assertThatThrownBy(() -> maker.make(progressive, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasNoCause();
    assertThatThrownBy(() -> maker.make(componentScans, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasNoCause();
    assertThatThrownBy(() -> maker.make(baseline, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasCauseInstanceOf(IOException.class);
    assertThatThrownBy(() -> maker.make(smallProgressive, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("점진적 JPEG의 계수 버퍼는 성분마다 샘플링으로 줄인 8×8 블록 수 × 128바이트로 잰다")
  void jpegCoefficientBytes() {
    // 4:2:0: Y 1500×1500 블록, Cb·Cr 750×750 블록씩
    assertThat(ThumbnailMaker.jpegCoefficientBytes(jpegHeaderOnly(0xC2, 12000, 12000, YCC_420, 1)))
        .isEqualTo(432_000_000L);
    assertThat(ThumbnailMaker.jpegCoefficientBytes(jpegHeaderOnly(0xC2, 12000, 12000, YCC_444, 1)))
        .isEqualTo(864_000_000L);
    // 한 스캔짜리 순차 JPEG과 JPEG가 아닌 것은 0
    assertThat(ThumbnailMaker.jpegCoefficientBytes(jpegHeaderOnly(0xC0, 12000, 12000, YCC_444, 3)))
        .isZero();
    assertThat(ThumbnailMaker.jpegCoefficientBytes("<html>".getBytes(UTF_8))).isZero();
  }

  @Test
  @DisplayName("계수 버퍼를 잴 때 디코더처럼 마커 앞의 다른 바이트, 0xFF 채움, FF 00을 건너뛰고 머리를 읽지 못하면 가장 크게 본다")
  void jpegCoefficientBytesSkipsLikeDecoder() {
    byte[] header = jpegHeaderOnly(0xC2, 12000, 12000, YCC_420, 1);
    ByteArrayOutputStream padded = new ByteArrayOutputStream();
    padded.write(header, 0, 2); // SOI
    padded.writeBytes(new byte[] {0x12, 0x34, (byte) 0xFF, 0x00, (byte) 0xFF, (byte) 0xFF});
    padded.write(header, 2, header.length - 2); // 0xFF로 시작하는 SOF
    // 길이 칸이 파일 밖을 가리키거나 SOS 전에 끝난다
    byte[] pointsOutside = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, (byte) 0xFF, 0};
    byte[] truncated = Arrays.copyOf(header, 10);

    assertThat(ThumbnailMaker.jpegCoefficientBytes(padded.toByteArray())).isEqualTo(432_000_000L);
    assertThat(ThumbnailMaker.jpegCoefficientBytes(pointsOutside)).isEqualTo(Long.MAX_VALUE);
    assertThat(ThumbnailMaker.jpegCoefficientBytes(truncated)).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  @DisplayName("상한 안의 실제 점진적 JPEG은 축소본을 만든다")
  void readsProgressiveJpegWithinLimit() throws IOException {
    BufferedImage image = filled(2000, 1000, BufferedImage.TYPE_INT_RGB, Color.ORANGE);
    byte[] progressive = jpeg(image, true);
    byte[] baseline = jpeg(image, false);

    BufferedImage thumb = read(maker.make(progressive, ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(640);
    assertThat(thumb.getHeight()).isEqualTo(320);
    // ImageIO가 쓴 실제 파일(JFIF, 양자화·허프만 표 포함)에서도 머리를 읽는다
    assertThat(ThumbnailMaker.jpegCoefficientBytes(progressive)).isPositive();
    assertThat(ThumbnailMaker.jpegCoefficientBytes(baseline)).isZero();
  }

  @Test
  @DisplayName("브라우저가 보여주는 형식(JPEG, PNG, GIF, WebP)이 아니면 읽을 수 없는 이미지로 본다")
  void otherFormatsThrow() throws IOException {
    BufferedImage image = filled(100, 100, BufferedImage.TYPE_INT_RGB, Color.ORANGE);
    for (String format : List.of("tiff", "bmp")) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      assertThat(ImageIO.write(image, format, out)).isTrue();

      assertThatThrownBy(() -> maker.make(out.toByteArray(), ThumbnailMaker.SMALL_WIDTH))
          .as(format)
          .isInstanceOf(UnreadableImageException.class)
          .hasNoCause();
    }
  }

  @Test
  @DisplayName("상한 안의 아주 큰 원본(12000×9000)은 줄여 읽어 축소본을 만든다")
  void readsLargeOriginalWithinLimit() throws IOException {
    byte[] original = png(new BufferedImage(12000, 9000, BufferedImage.TYPE_BYTE_BINARY));

    BufferedImage thumb = read(maker.make(original, ThumbnailMaker.SMALL_WIDTH));

    assertThat(thumb.getWidth()).isEqualTo(640);
    assertThat(thumb.getHeight()).isEqualTo(480);
  }

  @Test
  @DisplayName("목표 폭으로 줄여 읽어도 128MB를 넘는 긴 이미지는 펼치기 전에 읽을 수 없는 이미지로 본다(16비트는 픽셀당 8바이트)")
  void tooLargeTallImageThrows() {
    // 1279×16000을 640 폭으로: 배율 2면 가로 640 미만이라 배율 1로 읽는다.
    // 8비트 RGBA는 약 82MB라 거절하지 않고 읽다가(픽셀 데이터가 없어) 실패한다. 16비트 RGBA는 약 164MB라 펼치기 전에 거절한다.
    byte[] eightBit = pngHeaderOnly(1279, 16000, 8, 6);
    byte[] sixteenBit = pngHeaderOnly(1279, 16000, 16, 6);

    assertThatThrownBy(() -> maker.make(eightBit, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasCauseInstanceOf(IOException.class);
    assertThatThrownBy(() -> maker.make(sixteenBit, ThumbnailMaker.SMALL_WIDTH))
        .isInstanceOf(UnreadableImageException.class)
        .hasNoCause();
  }

  @Test
  @DisplayName("서브샘플링 배율은 가로가 최대 폭의 2배 이상 남는 가장 큰 정수다")
  void subsampling() {
    assertThat(ThumbnailMaker.subsampling(12000, 9000, 640, 4)).isEqualTo(9);
    assertThat(ThumbnailMaker.subsampling(1000, 800, 640, 4)).isEqualTo(1);
  }

  @Test
  @DisplayName("세로로 아주 긴 이미지는 줄여 읽은 장면이 64MB 안에 들 때까지 배율을 더 키운다")
  void subsamplingBoundsTallImage() {
    // 원본 상한(한 변 30000px, 1억 5000만 픽셀)과 WebP 상한(세로 7000px)을 지나 make()가 실제로 넘기는 크기다.
    // 처음 배율 2면 1280×14000×4 ≈ 71.7MB라 넘는다. 3이면 854×9334×4로 약 31.9MB다.
    assertThat(ThumbnailMaker.subsampling(2560, 28000, 640, 4)).isEqualTo(3);
  }

  @Test
  @DisplayName("상한을 넘어도 줄여 읽은 가로가 목표 폭보다 작아지게는 배율을 키우지 않는다")
  void subsamplingKeepsTargetWidth() {
    // 배율 1이면 68MB로 상한을 넘지만, 2면 가로 500이라 640으로 다시 키워야 하므로 1에서 멈춘다.
    assertThat(ThumbnailMaker.subsampling(1000, 17000, 640, 4)).isEqualTo(1);
    // 배율 2면 1280×16000(약 82MB)으로 상한을 넘지만, 3이면 가로 854라 1280보다 작으므로 2에서 멈춘다.
    assertThat(ThumbnailMaker.subsampling(2560, 32000, 1280, 4)).isEqualTo(2);
  }

  @Test
  @DisplayName("픽셀당 바이트가 큰 장면(16비트 RGBA)은 같은 크기라도 더 줄여 읽는다")
  void subsamplingCountsBytesPerPixel() {
    // 3000×3000은 4바이트면 36MB, 8바이트면 72MB로 상한(64MB)을 넘는다.
    assertThat(ThumbnailMaker.subsampling(3000, 3000, 1280, 4)).isEqualTo(1);
    assertThat(ThumbnailMaker.subsampling(3000, 3000, 1280, 8)).isEqualTo(2);
  }
}
