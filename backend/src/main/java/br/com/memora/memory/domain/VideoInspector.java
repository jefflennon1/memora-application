package br.com.memora.memory.domain;

import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.UnsupportedMediaException;
import br.com.memora.shared.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Identifica MP4/MOV pelo conteúdo e lê duração e dimensões sem confiar no navegador. */
public final class VideoInspector {

    public static final long MAX_DURATION_MS = 60_000;

    private VideoInspector() {
    }

    public record VideoInfo(String contentType, int width, int height, long durationMs) {
    }

    public static boolean looksLikeVideo(byte[] data) {
        if (data == null || data.length < 12 || !ascii(data, 4, 8).equals("ftyp")) return false;
        String brand = ascii(data, 8, 12);
        return !brand.startsWith("M4A") && !brand.startsWith("M4B")
                && !brand.startsWith("hei") && !brand.startsWith("hev")
                && !brand.equals("mif1") && !brand.equals("msf1") && !brand.startsWith("avi");
    }

    public static VideoInfo inspect(byte[] data) {
        if (!looksLikeVideo(data)) {
            throw new UnsupportedMediaException("UNSUPPORTED_MEDIA_TYPE", "Envie um vídeo MP4 ou MOV.");
        }
        try {
            List<Box> top = boxes(data, 0, data.length);
            Box ftyp = required(top, "ftyp");
            Box moov = required(top, "moov");
            required(top, "mdat");
            long durationMs = duration(data, required(boxes(data, moov.payloadStart(), moov.end()), "mvhd"));
            Dimensions dimensions = videoDimensions(data, boxes(data, moov.payloadStart(), moov.end()));
            if (durationMs <= 0 || dimensions == null || dimensions.width <= 0 || dimensions.height <= 0) {
                throw invalid();
            }
            if (durationMs > MAX_DURATION_MS) {
                throw new BusinessRuleException("VIDEO_TOO_LONG", "O vídeo pode ter no máximo 60 segundos.");
            }
            if (dimensions.width > ImageInspector.MAX_DIMENSION || dimensions.height > ImageInspector.MAX_DIMENSION) {
                throw new BusinessRuleException("VIDEO_DIMENSIONS_TOO_LARGE", "As dimensões do vídeo são grandes demais.");
            }
            String brand = ascii(data, ftyp.payloadStart(), Math.min(ftyp.payloadStart() + 4, ftyp.end()));
            String contentType = brand.equals("qt  ") ? "video/quicktime" : "video/mp4";
            return new VideoInfo(contentType, dimensions.width, dimensions.height, durationMs);
        } catch (BusinessRuleException | ValidationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    private static long duration(byte[] data, Box mvhd) {
        int payload = mvhd.payloadStart();
        int version = u8(data, payload);
        long timescale;
        long duration;
        if (version == 0) {
            timescale = u32(data, payload + 12);
            duration = u32(data, payload + 16);
        } else if (version == 1) {
            timescale = u32(data, payload + 20);
            duration = u64(data, payload + 24);
        } else {
            throw invalid();
        }
        if (timescale <= 0 || duration <= 0) throw invalid();
        return Math.round(duration * 1000.0 / timescale);
    }

    private static Dimensions videoDimensions(byte[] data, List<Box> moovChildren) {
        for (Box trak : moovChildren.stream().filter(box -> box.type.equals("trak")).toList()) {
            List<Box> track = boxes(data, trak.payloadStart(), trak.end());
            Box mdia = track.stream().filter(box -> box.type.equals("mdia")).findFirst().orElse(null);
            Box tkhd = track.stream().filter(box -> box.type.equals("tkhd")).findFirst().orElse(null);
            if (mdia == null || tkhd == null) continue;
            Box hdlr = boxes(data, mdia.payloadStart(), mdia.end()).stream()
                    .filter(box -> box.type.equals("hdlr")).findFirst().orElse(null);
            if (hdlr == null || !ascii(data, hdlr.payloadStart() + 8, hdlr.payloadStart() + 12).equals("vide")) {
                continue;
            }
            int width = (int) (u32(data, tkhd.end() - 8) >>> 16);
            int height = (int) (u32(data, tkhd.end() - 4) >>> 16);
            return new Dimensions(width, height);
        }
        return null;
    }

    private static List<Box> boxes(byte[] data, int start, int end) {
        if (start < 0 || end > data.length || start > end) throw invalid();
        List<Box> result = new ArrayList<>();
        int position = start;
        while (position < end) {
            if (end - position < 8) throw invalid();
            long size = u32(data, position);
            int header = 8;
            if (size == 1) {
                size = u64(data, position + 8);
                header = 16;
            } else if (size == 0) {
                size = end - position;
            }
            if (size < header || size > Integer.MAX_VALUE || position + size > end) throw invalid();
            result.add(new Box(ascii(data, position + 4, position + 8), position, (int) size, header));
            position += (int) size;
        }
        return result;
    }

    private static Box required(List<Box> boxes, String type) {
        return boxes.stream().filter(box -> box.type.equals(type)).findFirst().orElseThrow(VideoInspector::invalid);
    }

    private static int u8(byte[] data, int offset) {
        if (offset < 0 || offset >= data.length) throw invalid();
        return data[offset] & 0xFF;
    }

    private static long u32(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) throw invalid();
        return ((long) u8(data, offset) << 24) | ((long) u8(data, offset + 1) << 16)
                | ((long) u8(data, offset + 2) << 8) | u8(data, offset + 3);
    }

    private static long u64(byte[] data, int offset) {
        long high = u32(data, offset);
        long low = u32(data, offset + 4);
        if ((high & 0x80000000L) != 0) throw invalid();
        return (high << 32) | low;
    }

    private static String ascii(byte[] data, int from, int to) {
        if (from < 0 || to > data.length || from > to) throw invalid();
        return new String(data, from, to - from, StandardCharsets.ISO_8859_1);
    }

    private static ValidationException invalid() {
        return new ValidationException("INVALID_VIDEO", "O arquivo enviado não é um vídeo MP4/MOV válido.");
    }

    private record Box(String type, int start, int size, int header) {
        int payloadStart() {
            return start + header;
        }

        int end() {
            return start + size;
        }
    }

    private record Dimensions(int width, int height) {
    }
}
