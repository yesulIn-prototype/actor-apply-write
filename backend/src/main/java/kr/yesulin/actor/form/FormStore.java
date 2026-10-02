package kr.yesulin.actor.form;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared forms on disk, one folder per notice and one per version:
 * {@code <root>/<vid>/state.json} and {@code <root>/<vid>/v<n>/{source.hwp, definition.json, version.json}}.
 * A published version is never written again; editing one starts a new draft version.
 */
@Component
public final class FormStore {
    private static final String STATE = "state.json";
    private static final String SOURCE = "source.hwp";
    private static final String DEFINITION = "definition.json";
    private static final String INFO = "version.json";
    private final Path root;
    private final JsonMapper json;

    /** @param published versions ever published; the last one is what the link shows */
    public record State(List<Integer> published, boolean closed) {
        public State {
            published = List.copyOf(published);
        }

        Optional<Integer> current() {
            return published.isEmpty() ? Optional.empty() : Optional.of(published.getLast());
        }
    }

    /** @param testedHash fingerprint of the source and definition last test-built successfully, "" if none */
    public record VersionInfo(String originalName, String testedHash) {}

    public FormStore(@Value("${yesulin.forms-dir}") String root, JsonMapper json) throws IOException {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.json = json;
        Files.createDirectories(this.root);
    }

    synchronized List<Vid> all() throws IOException {
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("[0-9]{1,12}"))
                    .sorted(Comparator.comparingLong((String name) -> Long.parseLong(name)).reversed())
                    .map(Vid::new)
                    .toList();
        }
    }

    synchronized State state(Vid vid) throws IOException {
        Path file = folder(vid).resolve(STATE);
        return Files.exists(file) ? json.readValue(file.toFile(), State.class) : new State(List.of(), false);
    }

    synchronized List<Integer> versions(Vid vid) throws IOException {
        Path folder = folder(vid);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("v[0-9]{1,6}"))
                    .map(name -> Integer.parseInt(name.substring(1)))
                    .sorted()
                    .toList();
        }
    }

    /** The version the operator edits: the latest one, or a new copy of it once that one is published. */
    synchronized int draft(Vid vid) throws IOException {
        List<Integer> versions = versions(vid);
        if (!versions.isEmpty() && !state(vid).published().contains(versions.getLast())) {
            return versions.getLast();
        }
        int next = versions.isEmpty() ? 1 : versions.getLast() + 1;
        Path target = version(vid, next);
        Files.createDirectories(target);
        if (!versions.isEmpty()) {
            Path previous = version(vid, versions.getLast());
            for (String name : List.of(SOURCE, DEFINITION)) {
                if (Files.exists(previous.resolve(name))) {
                    Files.copy(previous.resolve(name), target.resolve(name));
                }
            }
            write(target.resolve(INFO), new VersionInfo(info(vid, versions.getLast()).originalName(), ""));
        }
        return next;
    }

    synchronized void saveSource(Vid vid, int number, String originalName, byte[] hwp) throws IOException {
        writeBytes(version(vid, number).resolve(SOURCE), hwp);
        write(version(vid, number).resolve(INFO), new VersionInfo(originalName, ""));
    }

    synchronized void saveDefinition(Vid vid, int number, String definition) throws IOException {
        writeBytes(version(vid, number).resolve(DEFINITION), definition.getBytes(StandardCharsets.UTF_8));
    }

    synchronized void markTested(Vid vid, int number, String hash) throws IOException {
        write(version(vid, number).resolve(INFO), new VersionInfo(info(vid, number).originalName(), hash));
    }

    synchronized void publish(Vid vid, int number) throws IOException {
        List<Integer> published = new ArrayList<>(state(vid).published());
        published.remove(Integer.valueOf(number));
        published.add(number);
        write(folder(vid).resolve(STATE), new State(published, false));
    }

    synchronized void close(Vid vid, boolean closed) throws IOException {
        write(folder(vid).resolve(STATE), new State(state(vid).published(), closed));
    }

    Optional<Path> source(Vid vid, int number) {
        Path source = version(vid, number).resolve(SOURCE);
        return Files.exists(source) ? Optional.of(source) : Optional.empty();
    }

    Optional<String> definition(Vid vid, int number) throws IOException {
        Path file = version(vid, number).resolve(DEFINITION);
        return Files.exists(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty();
    }

    VersionInfo info(Vid vid, int number) throws IOException {
        Path file = version(vid, number).resolve(INFO);
        return Files.exists(file) ? json.readValue(file.toFile(), VersionInfo.class) : new VersionInfo("", "");
    }

    /** The forms folder itself, for the backup; callers hold this store's lock while they read or add folders. */
    Path root() {
        return root;
    }

    /** Where the blank form's page images are rendered for the operator. */
    Path renderDirectory(Vid vid, int number) {
        return version(vid, number).resolve("render");
    }

    private Path folder(Vid vid) {
        Path folder = root.resolve(vid.value()).normalize();
        if (!folder.startsWith(root)) {
            throw new IllegalArgumentException("잘못된 공고 경로입니다.");
        }
        return folder;
    }

    private Path version(Vid vid, int number) {
        return folder(vid).resolve("v" + number);
    }

    private void write(Path file, Object value) throws IOException {
        writeBytes(file, json.writeValueAsBytes(value));
    }

    /** Written beside the target and moved over it, so a crash never leaves half a file. */
    private static void writeBytes(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Path draft = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(draft, content);
        Files.move(draft, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
