package br.com.memora.memory.domain;

import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.UnsupportedMediaException;
import br.com.memora.shared.error.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;

/** Identifica áudios pelo conteúdo e lê a duração sem confiar nos metadados enviados pelo cliente. */
public final class AudioInspector {

    public static final long MAX_DURATION_MS = 30_000;

    private AudioInspector() {
    }

    public record AudioInfo(String contentType, long durationMs, String extension) {
    }

    public static boolean looksLikeAudio(byte[] data) {
        return format(data) != null;
    }

    public static AudioInfo inspect(byte[] data) {
        Format format = format(data);
        if (format == null) {
            throw new UnsupportedMediaException("UNSUPPORTED_MEDIA_TYPE",
                    "Tipo de arquivo não aceito. Envie uma foto ou um áudio MP3, M4A, Ogg, FLAC ou WAV.");
        }
        Path temporary = null;
        try {
            temporary = Files.createTempFile("memora-audio-", "." + format.extension());
            Files.write(temporary, data);
            AudioFile audio = AudioFileIO.read(temporary.toFile());
            double seconds = audio.getAudioHeader().getPreciseTrackLength();
            long durationMs = Math.round(seconds * 1000.0);
            if (durationMs <= 0) {
                throw invalid();
            }
            if (durationMs > MAX_DURATION_MS) {
                throw new BusinessRuleException("AUDIO_TOO_LONG", "O áudio pode ter no máximo 30 segundos.");
            }
            return new AudioInfo(format.contentType(), durationMs, format.extension());
        } catch (BusinessRuleException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // O arquivo temporário também será removido pelo sistema operacional.
                }
            }
        }
    }

    private static Format format(byte[] data) {
        if (data == null || data.length < 4) return null;
        String first4 = ascii(data, 0, 4);
        if (first4.equals("RIFF") && ascii(data, 8, 12).equals("WAVE")) {
            return new Format("audio/wav", "wav");
        }
        if (first4.equals("OggS")) return new Format("audio/ogg", "ogg");
        if (first4.equals("fLaC")) return new Format("audio/flac", "flac");
        if (ascii(data, 0, 3).equals("ID3") || isMp3Frame(data)) {
            return new Format("audio/mpeg", "mp3");
        }
        if (ascii(data, 4, 8).equals("ftyp")) {
            String brand = ascii(data, 8, 12);
            if (brand.startsWith("M4A") || brand.startsWith("M4B")) {
                return new Format("audio/mp4", "m4a");
            }
        }
        return null;
    }

    private static boolean isMp3Frame(byte[] data) {
        return data.length >= 2 && (data[0] & 0xFF) == 0xFF && ((data[1] & 0xE0) == 0xE0);
    }

    private static String ascii(byte[] data, int from, int to) {
        if (from < 0 || to > data.length || from > to) return "";
        return new String(data, from, to - from, StandardCharsets.ISO_8859_1);
    }

    private static ValidationException invalid() {
        return new ValidationException("INVALID_AUDIO", "O arquivo enviado não é um áudio válido.");
    }

    private record Format(String contentType, String extension) {
    }
}
