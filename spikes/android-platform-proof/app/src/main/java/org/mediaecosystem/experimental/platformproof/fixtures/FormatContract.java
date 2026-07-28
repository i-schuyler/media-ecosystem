package org.mediaecosystem.experimental.platformproof.fixtures;

import java.util.List;
import java.util.Map;

public final class FormatContract {
    public static final String ID = "v1-required-formats-2026-07-28";
    public static final List<String> REQUIRED_IDS = List.of(
            "mp3-v0",
            "mp3-320",
            "flac",
            "aac",
            "ogg-vorbis",
            "wav"
    );
    public static final Map<String, String> REQUIRED_LABELS = Map.of(
            "mp3-v0", "MP3 V0",
            "mp3-320", "MP3 320",
            "flac", "FLAC",
            "aac", "AAC",
            "ogg-vorbis", "Ogg Vorbis",
            "wav", "WAV"
    );
    public static final List<String> HISTORICAL_NONREQUIRED_IDS = List.of("alac", "aiff");

    private FormatContract() {}

    public static boolean isRequired(String fixtureId) {
        return REQUIRED_LABELS.containsKey(fixtureId);
    }
}
