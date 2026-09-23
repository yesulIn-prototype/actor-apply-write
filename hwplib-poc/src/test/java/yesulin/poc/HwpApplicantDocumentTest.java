package yesulin.poc;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HwpApplicantDocumentTest {
    private HwpApplicantDocumentTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException(
                    "Usage: <original.hwp> <image1.jpg> <image2.jpg> <image3.jpg> <image4.jpg> <output-dir>");
        }

        Path original = Path.of(args[0]);
        Path image1 = Path.of(args[1]);
        Path image2 = Path.of(args[2]);
        Path image3 = Path.of(args[3]);
        Path image4 = Path.of(args[4]);
        Path outputDirectory = Path.of(args[5]);
        Files.createDirectories(outputDirectory);

        HwpApplicantDocument document = HwpApplicantDocument.open(original);
        check(document.tableCount() == 2, "expected two tables");
        check(document.rowCount(0) == 13, "expected 13 rows in the application table");
        check(document.rowCount(1) == 2, "expected two rows in the photo table");
        check(document.cellText(0, 0, 0).equals("이름"), "expected name label");
        check(document.cellText(1, 0, 0).equals("사진1"), "expected first photo label");

        HwpApplicantDocument roundTripDocument = HwpApplicantDocument.open(original);
        Path roundTripOutput = outputDirectory.resolve("roundtrip.hwp");
        roundTripDocument.save(roundTripOutput);
        HwpApplicantDocument reopenedRoundTrip = HwpApplicantDocument.open(roundTripOutput);
        check(reopenedRoundTrip.tableCount() == 2, "round-trip table count changed");

        document.setCellText(0, 0, 1, "테스트배우");
        Path textOutput = outputDirectory.resolve("text-test.hwp");
        document.save(textOutput);

        HwpApplicantDocument reopenedText = HwpApplicantDocument.open(textOutput);
        check(reopenedText.cellText(0, 0, 0).equals("이름"), "name label changed");
        check(reopenedText.cellText(0, 0, 1).equals("테스트배우"), "name value missing");

        HwpApplicantDocument multiFieldDocument = HwpApplicantDocument.open(original);
        multiFieldDocument.setCellText(0, 0, 1, "테스트배우");
        multiFieldDocument.setCellText(0, 0, 3, "2000년 1월 1일");
        multiFieldDocument.setCellText(0, 1, 1, "서울특별시 종로구 테스트로 1");
        multiFieldDocument.setCellText(0, 2, 2, "010-1234-5678");
        multiFieldDocument.setCellText(0, 3, 1, "test@example.com");
        multiFieldDocument.setCellText(0, 3, 3, "햄릿");
        multiFieldDocument.setCellText(0, 4, 1, "테스트대학교 졸업");
        multiFieldDocument.setCellText(0, 4, 3, "180cm / 70kg");
        Path multiFieldOutput = outputDirectory.resolve("multi-field-test.hwp");
        multiFieldDocument.save(multiFieldOutput);

        HwpApplicantDocument reopenedMultiField = HwpApplicantDocument.open(multiFieldOutput);
        check(reopenedMultiField.cellText(0, 0, 1).equals("테스트배우"), "multi-field name missing");
        check(reopenedMultiField.cellText(0, 0, 3).equals("2000년 1월 1일"), "birth date missing");
        check(reopenedMultiField.cellText(0, 1, 1).equals("서울특별시 종로구 테스트로 1"), "address missing");
        check(reopenedMultiField.cellText(0, 2, 2).equals("010-1234-5678"), "phone missing");
        check(reopenedMultiField.cellText(0, 3, 3).equals("햄릿"), "desired role missing");
        check(reopenedMultiField.cellText(0, 4, 3).equals("180cm / 70kg"), "height and weight missing");

        HwpApplicantDocument photoDocument = HwpApplicantDocument.open(original);
        int imagesBefore = photoDocument.embeddedImageCount();
        photoDocument.insertImageInCell(1, 0, 0, image1);
        Path photoOutput = outputDirectory.resolve("photo-test.hwp");
        photoDocument.save(photoOutput);

        HwpApplicantDocument reopenedPhoto = HwpApplicantDocument.open(photoOutput);
        check(reopenedPhoto.embeddedImageCount() == imagesBefore + 1, "embedded image missing");

        HwpApplicantDocument multiPhotoDocument = HwpApplicantDocument.open(original);
        multiPhotoDocument.insertImageInCell(1, 0, 0, image1);
        multiPhotoDocument.insertImageInCell(1, 0, 1, image2);
        multiPhotoDocument.insertImageInCell(1, 1, 0, image3);
        multiPhotoDocument.insertImageInCell(1, 1, 1, image4);
        Path multiPhotoOutput = outputDirectory.resolve("multi-photo-test.hwp");
        multiPhotoDocument.save(multiPhotoOutput);

        HwpApplicantDocument reopenedMultiPhoto = HwpApplicantDocument.open(multiPhotoOutput);
        check(reopenedMultiPhoto.embeddedImageCount() == imagesBefore + 4, "four embedded images missing");

        System.out.println("PASS: hwplib round-trip, text, multi-field, photo, and multi-photo tests");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
