package org.mediaecosystem.experimental.platformproof.fixtures;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FixtureCatalog {
    private final List<Fixture> fixtures;

    private FixtureCatalog(List<Fixture> fixtures) {
        this.fixtures = List.copyOf(fixtures);
    }

    public static FixtureCatalog load(Context context) {
        try (InputStream input = context.getAssets().open("fixtures/fixture-manifest.json")) {
            JSONObject manifest = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            JSONArray entries = manifest.getJSONArray("fixtures");
            List<Fixture> fixtures = new ArrayList<>();
            for (int index = 0; index < entries.length(); index++) {
                JSONObject entry = entries.getJSONObject(index);
                JSONObject metadata = entry.getJSONObject("expected_metadata");
                fixtures.add(new Fixture(
                        entry.getString("id"),
                        entry.getString("required_format"),
                        entry.getString("filename"),
                        entry.getString("mime_type"),
                        entry.getString("container"),
                        entry.getString("codec"),
                        entry.getString("sha256"),
                        entry.getLong("size_bytes"),
                        entry.getLong("expected_duration_ms"),
                        entry.getLong("duration_tolerance_ms"),
                        metadata.getString("title"),
                        metadata.getString("artist"),
                        metadata.getString("album")));
            }
            if (fixtures.size() != 8) {
                throw new IllegalStateException("Packaged fixture manifest does not contain exactly eight entries");
            }
            return new FixtureCatalog(fixtures);
        } catch (IOException | JSONException exception) {
            throw new IllegalStateException("Unable to load packaged fixture manifest", exception);
        }
    }

    public List<Fixture> fixtures() {
        return Collections.unmodifiableList(fixtures);
    }

    public Fixture byId(String id) {
        return fixtures.stream()
                .filter(fixture -> fixture.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown fixture: " + id));
    }

    public record Fixture(
            String id,
            String requiredFormat,
            String filename,
            String mimeType,
            String container,
            String codec,
            String sha256,
            long sizeBytes,
            long expectedDurationMs,
            long durationToleranceMs,
            String title,
            String artist,
            String album
    ) {
        public String assetUri() {
            return "asset:///fixtures/" + filename;
        }
    }
}
