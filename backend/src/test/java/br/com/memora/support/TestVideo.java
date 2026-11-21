package br.com.memora.support;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** MP4 estrutural mínimo, suficiente para testar a leitura segura dos metadados. */
public final class TestVideo {

    private TestVideo() {
    }

    public static byte[] mp4(int seconds, int width, int height) {
        byte[] ftyp = box("ftyp", concat(ascii("isom"), ints(0), ascii("isommp42")));
        byte[] mvhd = box("mvhd", concat(ints(0, 0, 0, 1_000, seconds * 1_000), new byte[80]));
        byte[] tkhd = box("tkhd", concat(new byte[76], ints(width << 16, height << 16)));
        byte[] hdlr = box("hdlr", concat(ints(0, 0), ascii("vide"), new byte[12]));
        byte[] trak = box("trak", concat(tkhd, box("mdia", hdlr)));
        byte[] moov = box("moov", concat(mvhd, trak));
        byte[] mdat = box("mdat", new byte[] {0, 0, 0, 1});
        return concat(ftyp, moov, mdat);
    }

    private static byte[] box(String type, byte[] payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(8 + payload.length);
            out.write(ascii(type));
            out.write(payload);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static byte[] ints(int... values) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            for (int value : values) out.writeInt(value);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] value : values) out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
