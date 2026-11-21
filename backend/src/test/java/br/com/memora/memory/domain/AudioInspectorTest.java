package br.com.memora.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.memora.shared.error.DomainException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class AudioInspectorTest {

    @Test
    void readsWavDurationFromTheFile() {
        AudioInspector.AudioInfo info = AudioInspector.inspect(wav(2));
        assertThat(info.contentType()).isEqualTo("audio/wav");
        assertThat(info.durationMs()).isBetween(1_990L, 2_010L);
    }

    @Test
    void rejectsAudioLongerThanThirtySeconds() {
        assertThatThrownBy(() -> AudioInspector.inspect(wav(31)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("AUDIO_TOO_LONG");
    }

    @Test
    void rejectsCorruptAudio() {
        byte[] corrupt = "RIFF....WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> AudioInspector.inspect(corrupt))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("INVALID_AUDIO");
    }

    private static byte[] wav(int seconds) {
        int sampleRate = 8_000;
        int dataSize = sampleRate * seconds * 2;
        ByteBuffer out = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        ascii(out, "RIFF");
        out.putInt(36 + dataSize);
        ascii(out, "WAVEfmt ");
        out.putInt(16);
        out.putShort((short) 1);
        out.putShort((short) 1);
        out.putInt(sampleRate);
        out.putInt(sampleRate * 2);
        out.putShort((short) 2);
        out.putShort((short) 16);
        ascii(out, "data");
        out.putInt(dataSize);
        return out.array();
    }

    private static void ascii(ByteBuffer out, String value) {
        out.put(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
