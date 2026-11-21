package br.com.memora.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** Imagens geradas em memória para os testes (nada de arquivos binários no repositório). */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode("png", width, height);
    }

    public static byte[] jpeg(int width, int height) {
        return encode("jpg", width, height);
    }

    public static byte[] gif(int width, int height) {
        return encode("gif", width, height);
    }

    /** JPEG com bloco EXIF contendo só a tag Orientation (como fotos de celular). */
    public static byte[] jpegWithOrientation(int width, int height, int orientation, boolean littleEndian) {
        byte[] jpeg = jpeg(width, height);
        byte[] tiff = littleEndian
                ? new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0,
                        (byte) orientation, 0, 0, 0, 0, 0, 0, 0}
                : new byte[] {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1,
                        0, (byte) orientation, 0, 0, 0, 0, 0, 0};
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int segmentLength = 2 + exifHeader.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write(segmentLength >> 8);
        out.write(segmentLength & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(tiff);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** Cabeçalho WebP "estendido" (VP8X) com as dimensões dadas. Suficiente para a inspeção, que não decodifica. */
    public static byte[] webpHeader(int width, int height) {
        byte[] data = new byte[40];
        put(data, 0, "RIFF");
        put(data, 8, "WEBP");
        put(data, 12, "VP8X");
        data[16] = 10;
        int w = width - 1;
        int h = height - 1;
        data[24] = (byte) w;
        data[25] = (byte) (w >> 8);
        data[26] = (byte) (w >> 16);
        data[27] = (byte) h;
        data[28] = (byte) (h >> 8);
        data[29] = (byte) (h >> 16);
        return data;
    }

    /** Começo de um MP4 (caixa "ftyp" com marca isom). */
    public static byte[] mp4Header() {
        byte[] data = new byte[32];
        data[3] = 24;
        put(data, 4, "ftyp");
        put(data, 8, "isom");
        return data;
    }

    /** Começo de um HEIC de iPhone. */
    public static byte[] heicHeader() {
        byte[] data = new byte[32];
        data[3] = 24;
        put(data, 4, "ftyp");
        put(data, 8, "heic");
        return data;
    }

    private static void put(byte[] data, int offset, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            data[offset + i] = (byte) ascii.charAt(i);
        }
    }

    private static byte[] encode(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xE8B04B));
        g.fillRect(0, 0, width, height);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("Sem codificador para " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
