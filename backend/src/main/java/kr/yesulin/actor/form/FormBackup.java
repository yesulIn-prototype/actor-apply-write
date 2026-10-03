package kr.yesulin.actor.form;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The operator's backup of every notice form: one zip laid out like the forms folder
 * ({@code <vid>/state.json}, {@code <vid>/v<n>/source.hwp|definition.json|version.json|spec.json}) plus
 * {@code backup.json}.
 * Rendered page images are left out; they are drawn again when needed. Restoring adds only the notices this
 * server does not have yet and never overwrites one, so a stale backup cannot undo newer work.
 */
@Component
public final class FormBackup {
    static final int FORMAT = 1;
    private static final String MANIFEST = "backup.json";
    private static final Pattern ENTRY = Pattern.compile(
            "^([0-9]{1,12})/(state\\.json|v[0-9]{1,6}/(source\\.hwp|definition\\.json|version\\.json|spec\\.json))$");
    /** Zip bombs: no file bigger than an upload may be, and a sane total. */
    private static final long MAX_FILE_BYTES = 25L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 500L * 1024L * 1024L;
    private static final int MAX_FILES = 20_000;

    private final FormStore store;
    private final JsonMapper json;

    public FormBackup(FormStore store, JsonMapper json) {
        this.store = store;
        this.json = json;
    }

    /** @param createdAt for the operator who opens the zip; restoring does not read it */
    record Manifest(int format, String createdAt, List<String> forms) {}

    /** @param restored notices added from the backup; skipped ones already exist here and were left alone */
    public record Restored(List<String> restored, List<String> skipped) {}

    public byte[] write() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // The store's own lock: no form is half-written while it is copied.
        synchronized (store) {
            List<Vid> forms = store.all();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                add(zip, MANIFEST, json.writeValueAsBytes(new Manifest(
                        FORMAT, Instant.now().toString(), forms.stream().map(Vid::value).toList())));
                for (Vid vid : forms) {
                    for (Path file : files(store.root().resolve(vid.value()))) {
                        add(zip, store.root().relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                    }
                }
            }
        }
        return bytes.toByteArray();
    }

    public Restored restore(InputStream upload) throws IOException {
        Path staging = store.root().resolve(".restore-" + UUID.randomUUID());
        try {
            unpack(upload, staging);
            List<String> restored = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            synchronized (store) {
                for (String vid : folders(staging)) {
                    Path target = store.root().resolve(vid);
                    if (Files.exists(target)) {
                        skipped.add(vid);
                    } else {
                        Files.move(staging.resolve(vid), target);
                        restored.add(vid);
                    }
                }
            }
            return new Restored(restored, skipped);
        } finally {
            deleteTree(staging);
        }
    }

    private void unpack(InputStream upload, Path staging) throws IOException {
        Files.createDirectories(staging);
        boolean manifest = false;
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(upload)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++count > MAX_FILES) {
                    throw new FormExceptions.InvalidBackup("백업 파일에 든 파일이 너무 많습니다.");
                }
                byte[] content = zip.readNBytes((int) MAX_FILE_BYTES + 1);
                if (content.length > MAX_FILE_BYTES || (total += content.length) > MAX_TOTAL_BYTES) {
                    throw new FormExceptions.InvalidBackup("백업 파일이 너무 큽니다.");
                }
                if (entry.getName().equals(MANIFEST)) {
                    manifest = format(content) == FORMAT;
                    continue;
                }
                Matcher name = ENTRY.matcher(entry.getName());
                if (!name.matches()) {
                    throw new FormExceptions.InvalidBackup("백업에 알 수 없는 파일이 있습니다: " + entry.getName());
                }
                check(name.group(2), content);
                Path file = staging.resolve(entry.getName()).normalize();
                Files.createDirectories(file.getParent());
                Files.write(file, content);
            }
        }
        if (!manifest) {
            throw new FormExceptions.InvalidBackup("예술in 공고 양식 백업 파일이 아닙니다.");
        }
    }

    private int format(byte[] manifest) {
        try {
            return json.readValue(manifest, Manifest.class).format();
        } catch (JacksonException unreadable) {
            return 0;
        }
    }

    /** The files the server reads back as JSON must read back. */
    private void check(String name, byte[] content) {
        try {
            if (name.equals("state.json")) {
                json.readValue(content, FormStore.State.class);
            } else if (name.endsWith("version.json")) {
                json.readValue(content, FormStore.VersionInfo.class);
            }
        } catch (JacksonException broken) {
            throw new FormExceptions.InvalidBackup("백업의 " + name + " 내용이 깨졌습니다.");
        }
    }

    private static void add(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    /** A notice's backed-up files: everything matching the layout, so render caches and drafts in progress stay out. */
    private List<Path> files(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> ENTRY.matcher(store.root().relativize(file).toString().replace('\\', '/')).matches())
                    .sorted()
                    .toList();
        }
    }

    private static List<String> folders(Path staging) throws IOException {
        try (Stream<Path> entries = Files.list(staging)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
