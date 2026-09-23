package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Renders a completed HWP to PDF, page images and page layout with the rhwp CLI (https://github.com/edwardkim/rhwp, MIT). */
@Component
public class PdfConverter {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private final Path executable;
    private final List<String> fontPaths;
    /** Rendering is CPU and memory heavy; a few at a time keeps the server responsive. */
    private final Semaphore slots = new Semaphore(2);

    @Autowired
    public PdfConverter(
            @Value("${yesulin.rhwp.path}") String executable,
            @Value("${yesulin.rhwp.font-paths:}") List<String> fontPaths) {
        this(Path.of(executable), fontPaths);
    }

    PdfConverter(Path executable, List<String> fontPaths) {
        this.executable = resolve(executable.toAbsolutePath().normalize());
        this.fontPaths = fontPaths.stream().filter(path -> !path.isBlank()).toList();
    }

    public boolean available() {
        return Files.isRegularFile(executable);
    }

    public void convert(Path hwp, Path pdf) throws IOException, HwpDocumentException {
        Files.deleteIfExists(pdf);
        run(List.of("export-pdf", hwp.toString(), "-o", pdf.toString()), true);
        if (!Files.isRegularFile(pdf) || Files.size(pdf) == 0) {
            throw new HwpDocumentException("PDF로 변환하지 못했습니다.");
        }
    }

    /** One SVG per page ("<name>_001.svg", …) into {@code directory}, for the on-screen preview. */
    public void exportSvg(Path hwp, Path directory) throws IOException, HwpDocumentException {
        Files.createDirectories(directory);
        run(List.of("export-svg", hwp.toString(), "-o", directory.toString()), true);
    }

    /** Per-page layout boxes ("render_tree_001.json", …): where every table cell landed on the page. */
    public void exportLayout(Path hwp, Path directory) throws IOException, HwpDocumentException {
        Files.createDirectories(directory);
        run(List.of("export-render-tree", hwp.toString(), "-o", directory.toString()), false);
    }

    private void run(List<String> arguments, boolean withFonts) throws IOException, HwpDocumentException {
        if (!available()) {
            throw new PdfUnavailableException();
        }
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(arguments);
        if (withFonts) {
            fontPaths.forEach(path -> command.addAll(List.of("--font-path", path)));
        }

        boolean acquired = false;
        Process process = null;
        try {
            acquired = slots.tryAcquire(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                throw new HwpDocumentException("문서 변환 대기 시간이 초과되었습니다.");
            }
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            byte[] log = process.getInputStream().readAllBytes();
            if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new HwpDocumentException("문서 변환 시간이 초과되었습니다.");
            }
            if (process.exitValue() != 0) {
                throw new HwpDocumentException(
                        "문서를 변환하지 못했습니다: " + new String(log, StandardCharsets.UTF_8).strip());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HwpDocumentException("문서 변환이 중단되었습니다.", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (acquired) {
                slots.release();
            }
        }
    }

    private static Path resolve(Path configured) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows && !Files.exists(configured) && !configured.toString().endsWith(".exe")) {
            return configured.resolveSibling(configured.getFileName() + ".exe");
        }
        return configured;
    }

    public static final class PdfUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PdfUnavailableException() {
            super("PDF 변환기가 설치되어 있지 않습니다. tools/install-rhwp.sh 를 실행해 주세요.");
        }
    }
}
