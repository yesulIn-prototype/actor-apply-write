package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Renders the completed form to page images with rhwp and maps each answer's cell to where it
 * landed on the page, so tapping the preview opens that answer for editing.
 */
@Service
public final class PreviewService {
    private final DocumentStore store;
    private final PdfConverter renderer;
    private final JsonMapper json;

    public PreviewService(DocumentStore store, PdfConverter renderer, JsonMapper json) {
        this.store = store;
        this.renderer = renderer;
        this.json = json;
    }

    public PreviewResponse preview(UUID documentId) throws IOException, HwpDocumentException {
        synchronized (store.require(documentId).directory()) {
            StoredDocument stored = store.require(documentId);
            Path manifest = directory(stored).resolve("preview.json");
            if (Files.exists(manifest)) {
                return json.readValue(manifest.toFile(), PreviewResponse.class);
            }
            PageLayout layout = PageLayout.render(renderer, json, completed(stored), directory(stored));
            List<PreviewResponse.Hotspot> hotspots = new ArrayList<>();
            for (EditTarget target : stored.targets()) {
                for (PageLayout.Box box : layout.cells().getOrDefault(target.address(), List.of())) {
                    hotspots.add(new PreviewResponse.Hotspot(
                            target.id(), box.page(), box.x(), box.y(), box.width(), box.height()));
                }
            }
            PreviewResponse preview = new PreviewResponse(layout.pages(), hotspots);
            json.writeValue(manifest.toFile(), preview);
            return preview;
        }
    }

    public byte[] page(UUID documentId, int number) throws IOException, HwpDocumentException {
        synchronized (store.require(documentId).directory()) {
            StoredDocument stored = store.require(documentId);
            preview(documentId);
            return pageImage(directory(stored), number);
        }
    }

    /**
     * Lays out any HWP (a blank shared form, for its operator) into {@code directory}, rendering it the
     * first time only.
     */
    public synchronized PageLayout layout(Path hwp, Path directory) throws IOException, HwpDocumentException {
        if (PageLayout.pageImages(directory).isEmpty()) {
            return PageLayout.render(renderer, json, hwp, directory);
        }
        return PageLayout.read(readTrees(directory), HwpDocument.open(hwp).cells());
    }

    /** One rendered page of {@link #layout}. */
    public byte[] pageImage(Path directory, int number) throws IOException {
        List<Path> svgs = PageLayout.pageImages(directory);
        if (number < 1 || number > svgs.size()) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        return Files.readAllBytes(svgs.get(number - 1));
    }

    /** Drops the rendered preview; called whenever the completed file changes. */
    static void invalidate(StoredDocument stored) throws IOException {
        deleteRendering(directory(stored));
    }

    /** Removes what {@link #layout} rendered into {@code directory}. */
    public static void deleteRendering(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private List<JsonNode> readTrees(Path directory) throws IOException {
        List<JsonNode> trees = new ArrayList<>();
        Path layout = directory.resolve("layout");
        try (Stream<Path> files = Files.list(layout)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                trees.add(json.readTree(file.toFile()));
            }
        }
        return trees;
    }

    private static Path completed(StoredDocument stored) {
        Path hwp = stored.completedHwp();
        if (!Files.exists(hwp)) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        return hwp;
    }

    private static Path directory(StoredDocument stored) {
        return stored.directory().resolve("preview");
    }
}
