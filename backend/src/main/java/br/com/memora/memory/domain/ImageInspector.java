package br.com.memora.memory.domain;

import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.UnsupportedMediaException;
import br.com.memora.shared.error.ValidationException;
import java.nio.charset.StandardCharsets;

/**
 * Identifica o tipo real de um arquivo pelos primeiros bytes (nunca pelo nome nem pelo {@code Content-Type}
 * informado pelo cliente) e lê as dimensões da imagem <b>sem decodificá-la</b>.
 *
 * <p>Aceitos aqui: JPEG, PNG, WebP e GIF. Vídeo e áudio são reconhecidos para que o serviço os encaminhe ao
 * inspetor correto. HEIC/AVIF são recusados pedindo outro formato. As dimensões devolvidas são as de exibição: em
 * JPEG, a orientação EXIF 5–8 (foto de celular "deitada") troca largura e altura.
 */
public final class ImageInspector {

    public static final int MAX_DIMENSION = 20_000;

    private ImageInspector() {
    }

    public record ImageInfo(String contentType, int width, int height) {
    }

    public static ImageInfo inspect(byte[] data) {
        Bytes b = new Bytes(data);
        if (b.startsWith(0xFF, 0xD8, 0xFF)) {
            return checked(jpeg(b));
        }
        if (b.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return checked(png(b));
        }
        if (b.ascii(0, 6).equals("GIF87a") || b.ascii(0, 6).equals("GIF89a")) {
            return checked(new ImageInfo("image/gif", b.u16le(6), b.u16le(8)));
        }
        if (b.ascii(0, 4).equals("RIFF")) {
            String format = b.ascii(8, 12);
            if (format.equals("WEBP")) {
                return checked(webp(b));
            }
            if (format.equals("WAVE")) {
                throw audio();
            }
            if (format.equals("AVI ")) {
                throw video();
            }
        }
        if (b.ascii(4, 8).equals("ftyp")) {
            String brand = b.ascii(8, 12);
            if (brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1")
                    || brand.startsWith("avi")) {
                throw new UnsupportedMediaException("IMAGE_FORMAT_NOT_SUPPORTED",
                        "Este formato de imagem (HEIC/AVIF) ainda não é aceito. Envie JPEG, PNG, WebP ou GIF.");
            }
            if (brand.startsWith("M4A") || brand.startsWith("M4B")) {
                throw audio();
            }
            throw video();
        }
        if (b.startsWith(0x1A, 0x45, 0xDF, 0xA3)) {
            throw video();
        }
        if (b.ascii(0, 3).equals("ID3") || b.ascii(0, 4).equals("OggS") || b.ascii(0, 4).equals("fLaC")
                || (b.length() >= 2 && b.u8(0) == 0xFF && (b.u8(1) & 0xE0) == 0xE0)) {
            throw audio();
        }
        throw new UnsupportedMediaException("UNSUPPORTED_MEDIA_TYPE",
                "Tipo de arquivo não aceito. Envie uma imagem JPEG, PNG, WebP ou GIF.");
    }

    // ------------------------------------------------------------------ formatos

    private static ImageInfo png(Bytes b) {
        if (!b.ascii(12, 16).equals("IHDR")) {
            throw invalid();
        }
        return new ImageInfo("image/png", b.u32be(16), b.u32be(20));
    }

    private static ImageInfo webp(Bytes b) {
        String chunk = b.ascii(12, 16);
        return switch (chunk) {
            case "VP8X" -> new ImageInfo("image/webp", 1 + b.u24le(24), 1 + b.u24le(27));
            case "VP8 " -> {
                if (!b.startsWithAt(23, 0x9D, 0x01, 0x2A)) {
                    throw invalid();
                }
                yield new ImageInfo("image/webp", b.u16le(26) & 0x3FFF, b.u16le(28) & 0x3FFF);
            }
            case "VP8L" -> {
                if (b.u8(20) != 0x2F) {
                    throw invalid();
                }
                long bits = b.u32le(21);
                yield new ImageInfo("image/webp", (int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1);
            }
            default -> throw invalid();
        };
    }

    private static ImageInfo jpeg(Bytes b) {
        int pos = 2;
        int orientation = 1;
        while (pos + 4 <= b.length()) {
            if (b.u8(pos) != 0xFF) {
                throw invalid();
            }
            int marker = b.u8(pos + 1);
            if (marker == 0xFF) {
                pos++;
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }
            int segmentLength = b.u16be(pos + 2);
            if (segmentLength < 2) {
                throw invalid();
            }
            if (marker == 0xE1 && orientation == 1) {
                orientation = exifOrientation(b, pos + 4, segmentLength - 2);
            }
            if (isStartOfFrame(marker)) {
                int height = b.u16be(pos + 5);
                int width = b.u16be(pos + 7);
                boolean rotated = orientation >= 5 && orientation <= 8;
                return new ImageInfo("image/jpeg", rotated ? height : width, rotated ? width : height);
            }
            pos += 2 + segmentLength;
        }
        throw invalid();
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    /** Tag 0x0112 (Orientation) do IFD0 do bloco EXIF; 1 (normal) se ausente ou ilegível. */
    private static int exifOrientation(Bytes b, int start, int length) {
        try {
            if (length < 14 || !b.ascii(start, start + 6).equals("Exif\0\0")) {
                return 1;
            }
            int tiff = start + 6;
            boolean little = b.ascii(tiff, tiff + 2).equals("II");
            if (!little && !b.ascii(tiff, tiff + 2).equals("MM")) {
                return 1;
            }
            long ifd = tiff + (little ? b.u32le(tiff + 4) : b.u32be(tiff + 4));
            int end = start + length;
            if (ifd + 2 > end) {
                return 1;
            }
            int entries = little ? b.u16le((int) ifd) : b.u16be((int) ifd);
            for (int i = 0; i < entries; i++) {
                int entry = (int) ifd + 2 + i * 12;
                if (entry + 12 > end) {
                    return 1;
                }
                int tag = little ? b.u16le(entry) : b.u16be(entry);
                if (tag == 0x0112) {
                    int value = little ? b.u16le(entry + 8) : b.u16be(entry + 8);
                    return value >= 1 && value <= 8 ? value : 1;
                }
            }
            return 1;
        } catch (ValidationException e) {
            return 1;
        }
    }

    private static ImageInfo checked(ImageInfo info) {
        if (info.width() <= 0 || info.height() <= 0) {
            throw invalid();
        }
        if (info.width() > MAX_DIMENSION || info.height() > MAX_DIMENSION) {
            throw new BusinessRuleException("IMAGE_DIMENSIONS_TOO_LARGE",
                    "A imagem é grande demais: cada lado pode ter no máximo " + MAX_DIMENSION + " pixels.");
        }
        return info;
    }

    private static ValidationException invalid() {
        return new ValidationException("INVALID_IMAGE", "O arquivo enviado não é uma imagem válida.");
    }

    private static UnsupportedMediaException video() {
        return new UnsupportedMediaException("VIDEO_FORMAT_NOT_SUPPORTED", "Use um vídeo MP4 ou MOV.");
    }

    private static UnsupportedMediaException audio() {
        return new UnsupportedMediaException("AUDIO_NOT_SUPPORTED_YET",
                "Áudios ainda não estão disponíveis na Memora. Por enquanto, envie fotos.");
    }

    /** Leitura de bytes com verificação de limites: ler fora do arquivo vira {@code INVALID_IMAGE}. */
    private record Bytes(byte[] data) {

        int length() {
            return data.length;
        }

        int u8(int i) {
            if (i < 0 || i >= data.length) {
                throw invalid();
            }
            return data[i] & 0xFF;
        }

        int u16be(int i) {
            return (u8(i) << 8) | u8(i + 1);
        }

        int u16le(int i) {
            return u8(i) | (u8(i + 1) << 8);
        }

        int u24le(int i) {
            return u8(i) | (u8(i + 1) << 8) | (u8(i + 2) << 16);
        }

        int u32be(int i) {
            long v = ((long) u8(i) << 24) | ((long) u8(i + 1) << 16) | ((long) u8(i + 2) << 8) | u8(i + 3);
            return v > Integer.MAX_VALUE ? -1 : (int) v;
        }

        long u32le(int i) {
            return u8(i) | ((long) u8(i + 1) << 8) | ((long) u8(i + 2) << 16) | ((long) u8(i + 3) << 24);
        }

        boolean startsWith(int... prefix) {
            return startsWithAt(0, prefix);
        }

        boolean startsWithAt(int offset, int... prefix) {
            if (data.length < offset + prefix.length) {
                return false;
            }
            for (int i = 0; i < prefix.length; i++) {
                if ((data[offset + i] & 0xFF) != prefix[i]) {
                    return false;
                }
            }
            return true;
        }

        /** Trecho como texto ASCII; vazio se estiver fora do arquivo. */
        String ascii(int from, int to) {
            if (from < 0 || to > data.length || from > to) {
                return "";
            }
            return new String(data, from, to - from, StandardCharsets.ISO_8859_1);
        }
    }
}
