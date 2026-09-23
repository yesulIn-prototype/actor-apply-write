package kr.yesulin.actor.document;

import java.util.List;
import java.util.Set;

/** Recognizes a tall, blank portrait cell next to the applicant's name, even without a photo label. */
final class UnlabeledPhotoExtractor {
    private UnlabeledPhotoExtractor() {}

    static void extract(FieldExtractor.Layout layout, List<FieldCandidate> fields, Set<CellAddress> claimed) {
        for (List<CellSnapshot> row : layout.rows()) {
            boolean identityRow = row.stream().anyMatch(cell ->
                    CellText.normalize(cell.text()).matches("^(이름|성명)(\\s.*)?$"));
            if (!identityRow) {
                continue;
            }
            for (CellSnapshot cell : row) {
                if (cell.text().isBlank() && cell.columnAddress() == 0 && cell.columnSpan() >= 2
                        && cell.rowSpan() >= 3 && cell.address().rowIndex() <= 2
                        && !claimed.contains(cell.address())) {
                    claimed.add(cell.address());
                    fields.add(FieldExtractor.field(FieldCandidate.FieldKind.PHOTO, "사진", "", cell,
                            FieldCandidate.InputStyle.BLANK, false));
                }
            }
        }
    }
}
