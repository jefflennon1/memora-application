package br.com.memora.support;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class TestAudio {

    private TestAudio() {
    }

    public static byte[] wav(int seconds) {
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
        out.put(value.getBytes(StandardCharsets.US_ASCII));
    }
}
