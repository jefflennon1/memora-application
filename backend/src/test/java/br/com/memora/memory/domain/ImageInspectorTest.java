package br.com.memora.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.memora.memory.domain.ImageInspector.ImageInfo;
import br.com.memora.shared.error.DomainException;
import br.com.memora.support.TestImages;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImageInspectorTest {

    @Test
    void recognizesPngJpegAndGifWithTheirDimensions() {
        assertThat(ImageInspector.inspect(TestImages.png(320, 200))).isEqualTo(new ImageInfo("image/png", 320, 200));
        assertThat(ImageInspector.inspect(TestImages.jpeg(640, 480))).isEqualTo(new ImageInfo("image/jpeg", 640, 480));
        assertThat(ImageInspector.inspect(TestImages.gif(10, 20))).isEqualTo(new ImageInfo("image/gif", 10, 20));
    }

    @Test
    void recognizesExtendedWebp() {
        assertThat(ImageInspector.inspect(TestImages.webpHeader(1920, 1080)))
                .isEqualTo(new ImageInfo("image/webp", 1920, 1080));
    }

    @ParameterizedTest
    @CsvSource({"1,true,640,480", "3,true,640,480", "6,true,480,640", "8,false,480,640", "5,false,480,640"})
    void jpegExifOrientationGivesDisplayDimensions(int orientation, boolean littleEndian, int width, int height) {
        ImageInfo info = ImageInspector.inspect(TestImages.jpegWithOrientation(640, 480, orientation, littleEndian));
        assertThat(info.width()).isEqualTo(width);
        assertThat(info.height()).isEqualTo(height);
    }

    @Test
    void videosAudiosAndHeicAreRejectedWithClearCodes() {
        assertCode(TestImages.mp4Header(), "VIDEO_FORMAT_NOT_SUPPORTED");
        assertCode(new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0}, "VIDEO_FORMAT_NOT_SUPPORTED");
        assertCode("ID3\u0004\0\0\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1), "AUDIO_NOT_SUPPORTED_YET");
        assertCode("OggS\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1), "AUDIO_NOT_SUPPORTED_YET");
        assertCode(TestImages.heicHeader(), "IMAGE_FORMAT_NOT_SUPPORTED");
    }

    @Test
    void contentDecidesNotTheExtension() {
        assertCode("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8),
                "UNSUPPORTED_MEDIA_TYPE");
        assertCode("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), "UNSUPPORTED_MEDIA_TYPE");
        assertCode(new byte[0], "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void truncatedOrCorruptImagesAreInvalid() {
        byte[] png = TestImages.png(10, 10);
        assertCode(Arrays.copyOf(png, 14), "INVALID_IMAGE");
        byte[] jpeg = TestImages.jpeg(10, 10);
        assertCode(Arrays.copyOf(jpeg, 6), "INVALID_IMAGE");
        assertCode(zeroDimensionPng(), "INVALID_IMAGE");
    }

    @Test
    void absurdDimensionsAreRejected() {
        assertCode(TestImages.webpHeader(30_000, 100), "IMAGE_DIMENSIONS_TOO_LARGE");
    }

    private static byte[] zeroDimensionPng() {
        byte[] png = TestImages.png(10, 10);
        Arrays.fill(png, 16, 20, (byte) 0);
        return png;
    }

    private static void assertCode(byte[] data, String code) {
        assertThatThrownBy(() -> ImageInspector.inspect(data))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }
}
