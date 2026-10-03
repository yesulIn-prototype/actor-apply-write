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
    private static final int LOG_SHOWN = 500;
    private final Path executable;
    private final List<String> fontPaths;
    private final Duration timeout;
    /** Rendering is CPU and memory heavy; a few at a time keeps the server responsive. */
    private final Semaphore slots = new Semaphore(2);

    @Autowired
    public PdfConverter(
            @Value("${yesulin.rhwp.path}") String executable,
            @Value("${yesulin.rhwp.font-paths:}") List<String> fontPaths) {
        this(Path.of(executable), fontPaths);
    }

    PdfConverter(Path executable, List<String> fontPaths) {
        this(executable, fontPaths, TIMEOUT);
    }

    /** @param timeout longest one rhwp run, and longest wait for a free slot, may take */
    PdfConverter(Path executable, List<String> fontPaths, Duration timeout) {
        this.executable = resolve(executable.toAbsolutePath().normalize());
        this.fontPaths = fontPaths.stream().filter(path -> !path.isBlank()).toList();
        this.timeout = timeout;
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

    /** HWPX (or a read-only 배포용 HWP) to an editable HWP 5 file, checked by re-parsing (--verify). */
    public void toHwp(Path source, Path hwp) throws IOException, HwpDocumentException {
        Files.deleteIfExists(hwp);
        run(List.of("convert", source.toString(), hwp.toString(), "--verify"), false);
        if (!Files.isRegularFile(hwp) || Files.size(hwp) == 0) {
            throw new HwpDocumentException("HWPX 문서를 변환하지 못했습니다.");
        }
    }

    /** A copy of row {@code row} of table {@code table} (top-level tables, export-tables numbering), right under it. */
    public void insertRow(Path hwp, Path output, int table, int row) throws IOException, HwpDocumentException {
        Files.deleteIfExists(output);
        run(List.of("edit", "insert-row", hwp.toString(), "--table", Integer.toString(table),
                "--row", Integer.toString(row), "--below", "-o", output.toString()), false);
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new HwpDocumentException("표에 줄을 더하지 못했습니다.");
        }
    }

    /** Removes row {@code row} of table {@code table} (top-level tables, export-tables numbering). */
    public void deleteRow(Path hwp, Path output, int table, int row) throws IOException, HwpDocumentException {
        Files.deleteIfExists(output);
        run(List.of("edit", "delete-row", hwp.toString(), "--table", Integer.toString(table),
                "--row", Integer.toString(row), "-o", output.toString()), false);
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new HwpDocumentException("표 줄을 지우지 못했습니다.");
        }
    }

    /** Replaces {@code find} everywhere in the body and tables; it must occur at least once. */
    public void replaceText(Path hwp, Path output, String find, String replace) throws IOException, HwpDocumentException {
        Files.deleteIfExists(output);
        run(List.of("edit", "replace-text", hwp.toString(), "--find", find, "--replace", replace,
                "-o", output.toString()), false);
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new HwpDocumentException("문서에서 '" + find + "'를 찾지 못했습니다.");
        }
    }

    /** Merges columns {@code fromColumn}..{@code toColumn} of one row (export-tables grid coordinates). */
    public void mergeCells(Path hwp, Path output, int table, int row, int fromColumn, int toColumn)
            throws IOException, HwpDocumentException {
        Files.deleteIfExists(output);
        run(List.of("edit", "merge-cells", hwp.toString(), "--table", Integer.toString(table),
                "--row", Integer.toString(row), "--col", Integer.toString(fromColumn),
                "--end-row", Integer.toString(row), "--end-col", Integer.toString(toColumn),
                "-o", output.toString()), false);
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new HwpDocumentException("표 칸을 합치지 못했습니다.");
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

    /**
     * rhwp's page plan of a file (dump-pages JSON): what lands on each page and how much of the page it uses.
     * Read only; the file is not changed.
     */
    public String pages(Path hwp) throws IOException, HwpDocumentException {
        return run(List.of("dump-pages", hwp.toString(), "--json"), false);
    }

    /** @return what rhwp printed (its standard output) */
    private String run(List<String> arguments, boolean withFonts) throws IOException, HwpDocumentException {
        if (!available()) {
            throw new PdfUnavailableException();
        }
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(arguments);
        if (withFonts) {
            fontPaths.forEach(path -> command.addAll(List.of("--font-path", path)));
        }

        // Output goes to files, not pipes: reading a pipe to its end would wait for rhwp however long it
        // runs, and the time limit below would never apply. Warnings stay apart from what a command prints.
        Path output = Files.createTempFile("rhwp-", ".out");
        Path errors = Files.createTempFile("rhwp-", ".err");
        boolean acquired = false;
        Process process = null;
        try {
            acquired = slots.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new HwpDocumentException("문서 변환 대기 시간이 초과되었습니다.");
            }
            process = new ProcessBuilder(command)
                    .redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
            process.getOutputStream().close();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process);
                throw new HwpDocumentException("문서 변환 시간이 초과되었습니다.");
            }
            if (process.exitValue() != 0) {
                throw new HwpDocumentException("문서를 변환하지 못했습니다: " + shown(errors, output));
            }
            return Files.readString(output, StandardCharsets.UTF_8);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HwpDocumentException("문서 변환이 중단되었습니다.", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            if (acquired) {
                slots.release();
            }
            deleteOutput(output);
            deleteOutput(errors);
        }
    }

    /** Kills rhwp and anything it started, and waits briefly for them to go so their output file is free. */
    private static void stop(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
    }

    /** The end of rhwp's messages, without job ids: its workspace paths name the applicant's job. */
    private static String shown(Path errors, Path output) throws IOException {
        String text = (Files.readString(errors, StandardCharsets.UTF_8) + "\n"
                + Files.readString(output, StandardCharsets.UTF_8)).strip();
        String tail = text.length() > LOG_SHOWN ? "…" + text.substring(text.length() - LOG_SHOWN) : text;
        return JobIds.masked(tail);
    }

    private static void deleteOutput(Path output) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException stillOpen) { // no-excuse-ok: catch - a process killed on Windows can hold the file a moment; the OS clears temp files
            output.toFile().deleteOnExit();
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
