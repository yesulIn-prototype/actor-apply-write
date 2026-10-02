package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Rows added with rhwp to the synthetic sample form (forms/sample-notice.hwp). */
class RowInserterTest {
    private final PdfConverter rhwp = new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp"), List.of());
    private final RowInserter inserter = new RowInserter(rhwp);

    @TempDir
    Path directory;

    private Path sample;

    @BeforeEach
    void copySample() throws IOException {
        assumeTrue(rhwp.available(), "rhwp not installed — run tools/install-rhwp.sh");
        sample = directory.resolve("sample.hwp");
        try (InputStream input = RowInserterTest.class.getResourceAsStream("/forms/sample-notice.hwp")) {
            Files.copy(input, sample);
        }
    }

    @Test
    @DisplayName("병합된 값 칸이 있는 줄을 늘리면 새 줄도 같은 모양으로 합쳐지고 아래 줄은 밀린다")
    void grow_MergesNewRowsLikeTheCopiedRow_WhenItsValueCellSpansColumns() throws Exception {
        // given: row 7 is "비상연락처 | value over 4 columns"
        // when
        Path grown = inserter.grow(sample, directory, List.of(new TableGrowth(0, 7, 2)));

        // then
        List<CellSnapshot> cells = HwpDocument.open(grown).cells();
        for (int row = 8; row <= 9; row++) {
            int at = row;
            assertThat(cells.stream().filter(cell -> cell.address().rowIndex() == at))
                    .extracting(CellSnapshot::columnSpan).containsExactly(1, 4);
        }
        assertThat(cells).anyMatch(cell -> cell.address().equals(new CellAddress(0, 10, 0)) && cell.text().equals("자기소개"));
    }

    @Test
    @DisplayName("위 줄과 병합된 칸이 걸친 줄은 늘리지 않고 이유를 알린다")
    void grow_Refuses_WhenRowSharesACellWithRowsAbove() throws IOException {
        // row 4 sits inside the career cell (rows 3–5) and the photo cell (rows 0–5)
        assertThatThrownBy(() -> inserter.grow(sample, directory, List.of(new TableGrowth(0, 4, 1))))
                .isInstanceOf(HwpDocumentException.class).hasMessageContaining("병합된 칸");
        try (var files = Files.list(directory)) {
            assertThat(files).containsExactly(sample);
        }
    }
}
