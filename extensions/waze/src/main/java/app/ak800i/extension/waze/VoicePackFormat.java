package app.ak800i.extension.waze;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The voice pack zip format.
 *
 * <pre>
 * my-voice.zip
 * ├── voicepack.json      optional metadata
 * ├── TurnLeft.mp3        one file per prompt, named after Waze's prompt id
 * ├── TurnRight.mp3
 * └── ...
 * </pre>
 *
 * Import is lenient: prompt files may sit in any folder of the zip, prompt ids are matched
 * without regard to case, and files that are not Waze prompts are skipped.
 */
final class VoicePackFormat {
    static final String MANIFEST_NAME = "voicepack.json";
    static final String FORMAT_ID = "waze-voicepack";
    static final int FORMAT_VERSION = 1;
    /** Waze limits a voice name to 40 characters. */
    static final int MAX_NAME_LENGTH = 40;

    private static final long MAX_PROMPT_BYTES = 8L * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final int MAX_ENTRIES = 2000;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_]{1,64}");

    static final class ImportResult {
        /** The name from the metadata, or null. */
        String name;
        /** Whether the metadata says the pack uses a newer format version than this patch knows. */
        boolean newerFormat;
        final List<String> imported = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
    }

    private VoicePackFormat() {
    }

    /**
     * Reads a voice pack and writes its prompts to {@code staging} as {@code <promptId>.mp3}.
     *
     * @param promptIds Waze's prompt ids. If empty, any file with a plain id-like name is accepted.
     */
    static ImportResult read(InputStream in, List<String> promptIds, File staging) throws IOException {
        Map<String, String> known = new HashMap<>();
        for (String id : promptIds) known.put(id.toLowerCase(Locale.ROOT), id);

        ImportResult result = new ImportResult();
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("The zip has too many files");
                if (entry.isDirectory() || entry.getName().contains("__MACOSX")) continue;

                // Only the base name is used, so a zip cannot write outside the staging folder.
                String base = baseName(entry.getName());
                if (base.isEmpty() || base.startsWith(".")) continue;

                if (base.equalsIgnoreCase(MANIFEST_NAME)) {
                    readManifest(readLimited(zip, MAX_MANIFEST_BYTES), result);
                    continue;
                }
                String lower = base.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".mp3")) {
                    result.skipped.add(base);
                    continue;
                }
                String id = resolveId(base.substring(0, base.length() - 4), known);
                if (id == null) {
                    result.skipped.add(base);
                    continue;
                }
                if (result.imported.contains(id)) {
                    result.skipped.add(base + " (duplicate)");
                    continue;
                }
                copyLimited(zip, new File(staging, id + ".mp3"), MAX_PROMPT_BYTES, base);
                result.imported.add(id);
            }
        }
        return result;
    }

    /** Writes the recordings of {@code voices} as a voice pack and returns how many prompts it holds. */
    static int write(OutputStream out, WazeVoices voices, String name, String wazeVersion) throws IOException {
        List<String> ids = voices.recordedPromptIds();
        if (ids.isEmpty()) return 0;
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(MANIFEST_NAME));
            zip.write(manifest(name, wazeVersion, ids).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            byte[] buffer = new byte[64 * 1024];
            for (String id : ids) {
                zip.putNextEntry(new ZipEntry(id + ".mp3"));
                try (InputStream in = new FileInputStream(voices.recording(id))) {
                    int n;
                    while ((n = in.read(buffer)) > 0) zip.write(buffer, 0, n);
                }
                zip.closeEntry();
            }
        }
        return ids.size();
    }

    static String manifest(String name, String wazeVersion, List<String> ids) {
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            JSONObject app = new JSONObject()
                    .put("package", "com.waze")
                    .put("version", wazeVersion == null ? JSONObject.NULL : wazeVersion);
            return new JSONObject()
                    .put("format", FORMAT_ID)
                    .put("version", FORMAT_VERSION)
                    .put("name", name)
                    .put("createdAt", iso.format(new Date()))
                    .put("app", app)
                    .put("prompts", new JSONArray(ids))
                    .toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static String trimName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH).trim() : trimmed;
    }

    private static void readManifest(byte[] bytes, ImportResult result) throws IOException {
        JSONObject json;
        try {
            json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException(MANIFEST_NAME + " is not valid JSON");
        }
        String format = json.optString("format", "");
        if (!format.isEmpty() && !format.equals(FORMAT_ID)) {
            throw new IOException("Not a Waze voice pack (format \"" + format + "\")");
        }
        result.newerFormat = json.optInt("version", FORMAT_VERSION) > FORMAT_VERSION;
        result.name = trimName(json.optString("name", ""));
    }

    private static String resolveId(String id, Map<String, String> known) {
        if (!known.isEmpty()) return known.get(id.toLowerCase(Locale.ROOT));
        // Without Waze's list, accept plain ids but never Waze's own unsaved "_temp" recordings.
        if (!SAFE_ID.matcher(id).matches() || id.toLowerCase(Locale.ROOT).endsWith("_temp")) return null;
        return id;
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(slash + 1);
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
            if (out.size() + n > limit) throw new IOException(MANIFEST_NAME + " is too large");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void copyLimited(InputStream in, File to, long limit, String label) throws IOException {
        long total = 0;
        try (OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > limit) throw new IOException(label + " is larger than 8 MB");
                out.write(buffer, 0, n);
            }
        }
        if (total == 0) {
            to.delete();
            throw new IOException(label + " is empty");
        }
    }
}
