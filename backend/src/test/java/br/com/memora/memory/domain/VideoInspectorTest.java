package br.com.memora.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.memora.shared.error.DomainException;
import br.com.memora.support.TestVideo;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class VideoInspectorTest {

    @Test
    void readsMp4DurationAndDimensions() {
        assertThat(VideoInspector.inspect(TestVideo.mp4(60, 1920, 1080)))
                .isEqualTo(new VideoInspector.VideoInfo("video/mp4", 1920, 1080, 60_000));
    }

    @Test
    void rejectsVideosLongerThanSixtySeconds() {
        assertCode(TestVideo.mp4(61, 1280, 720), "VIDEO_TOO_LONG");
    }

    @Test
    void rejectsTruncatedOrMetadataOnlyFiles() {
        byte[] video = TestVideo.mp4(10, 640, 480);
        assertCode(Arrays.copyOf(video, video.length - 10), "INVALID_VIDEO");
    }

    private static void assertCode(byte[] data, String code) {
        assertThatThrownBy(() -> VideoInspector.inspect(data))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(code);
    }
}
