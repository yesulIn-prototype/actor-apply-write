package kr.yesulin.actor.document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.paragraph.charshape.ParaCharShape;
import kr.dogfoot.hwplib.object.docinfo.CharShape;
import kr.dogfoot.hwplib.object.docinfo.charshape.UnderLineSort;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharType;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.ParaText;

/** Clears guide text ("년 월 일", "사진1", multi-line notes) from a cell while keeping its formatting. */
final class CellContent {
    private CellContent() {}

    /**
     * Returns the first paragraph, emptied, ready to receive the new value.
     *
     * <p>Text cells keep their empty paragraphs: forms size boxes like "자기소개" with blank lines, and
     * dropping them would shrink the box and shift the page. Photo cells drop them so the photo is
     * centered in the cell's own height.
     */
    static Paragraph clear(Cell cell, boolean keepLines) {
        var paragraphs = cell.getParagraphList();
        if (paragraphs.getParagraphCount() == 0) {
            paragraphs.addNewParagraph();
        }
        for (int index = paragraphs.getParagraphCount() - 1; index > 0; index--) {
            Paragraph paragraph = paragraphs.getParagraph(index);
            if (!keepLines) {
                removeDrawings(paragraph);
            }
            if (keepLines || hasControls(paragraph)) {
                clearText(paragraph);
            } else {
                paragraphs.deleteParagraph(index);
            }
        }
        Paragraph first = paragraphs.getParagraph(0);
        if (!keepLines) {
            removeDrawings(first);
        }
        clearText(first);
        return first;
    }

    /** A photo cell of an already completed form holds the old photo: it is replaced, not stacked. */
    private static void removeDrawings(Paragraph paragraph) {
        if (!hasControls(paragraph) || paragraph.getText() == null) {
            return;
        }
        var controls = paragraph.getControlList();
        var characters = paragraph.getText().getCharList();
        int extendIndex = 0;
        for (int index = 0; index < characters.size(); index++) {
            var character = characters.get(index);
            if (character.getType() != HWPCharType.ControlExtend) {
                continue;
            }
            if (extendIndex < controls.size() && controls.get(extendIndex).getType() == ControlType.Gso) {
                controls.remove(extendIndex);
                characters.remove(index);
                index--;
            } else {
                extendIndex++;
            }
        }
    }

    private static void clearText(Paragraph paragraph) {
        paragraph.deleteLineSeg();
        if (paragraph.getText() == null) {
            return;
        }
        paragraph.getText().getCharList().removeIf(character ->
                (character.getType() == HWPCharType.Normal && character.getCode() != 0x0d)
                        || character.isLineBreak());
        if (paragraph.getText().getCharList().isEmpty()) {
            // An empty text record cannot be written; a paragraph without one is a blank line.
            paragraph.deleteText();
        }
    }

    private static boolean hasControls(Paragraph paragraph) {
        return paragraph.getControlList() != null && !paragraph.getControlList().isEmpty();
    }

    /** Finds the body's most common Korean face, excluding cells with no text or valid shape. */
    static int representativeShapeId(HWPFile file, Iterable<Cell> cells) {
        List<CharShape> shapes = file.getDocInfo().getCharShapeList();
        int faceCount = file.getDocInfo().getHangulFaceNameList().size();
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        Map<Integer, Integer> firstShape = new LinkedHashMap<>();
        for (Cell cell : cells) {
            for (Paragraph paragraph : cell.getParagraphList()) {
                if (paragraph.getText() == null) {
                    continue;
                }
                int shapeId = firstShapeId(paragraph, shapes.size());
                if (shapeId < 0) {
                    continue;
                }
                int faceId = shapes.get(shapeId).getFaceNameIds().getHangul();
                if (faceId < 0 || faceId >= faceCount) {
                    continue;
                }
                counts.merge(faceId, 1, Integer::sum);
                firstShape.putIfAbsent(faceId, shapeId);
            }
        }
        int bestFace = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestFace = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return bestFace < 0 ? -1 : firstShape.get(bestFace);
    }

    /** Replaces a cell's old guide/text style with the form's uniform applicant-text style. */
    static void useInputStyle(HWPFile file, Paragraph paragraph, int representativeShapeId, int typicalSize) {
        int shapeId = addInputShape(file, paragraph, representativeShapeId, typicalSize);
        if (shapeId < 0) {
            return;
        }
        if (paragraph.getCharShape() == null) {
            paragraph.createCharShape();
        }
        ParaCharShape runs = paragraph.getCharShape();
        runs.getPositonShapeIdPairList().clear();
        runs.addParaCharShape(0, shapeId);
    }

    /** A label cell keeps its existing runs; only the newly appended value gets the input style. */
    static void styleAppendedText(
            HWPFile file, Paragraph paragraph, int representativeShapeId, int typicalSize, int start) {
        int shapeId = addInputShape(file, paragraph, representativeShapeId, typicalSize);
        if (shapeId < 0) {
            return;
        }
        if (paragraph.getCharShape() == null) {
            paragraph.createCharShape();
        }
        paragraph.getCharShape().addParaCharShape(start, shapeId);
    }

    private static int addInputShape(HWPFile file, Paragraph paragraph, int representativeShapeId, int typicalSize) {
        List<CharShape> shapes = file.getDocInfo().getCharShapeList();
        int sourceId = representativeShapeId >= 0 && representativeShapeId < shapes.size()
                ? representativeShapeId : firstShapeId(paragraph, shapes.size());
        if (sourceId < 0) {
            return -1;
        }
        CharShape input = shapes.get(sourceId).clone();
        input.getCharColor().setValue(0);
        if (typicalSize > 0) {
            input.setBaseSize(typicalSize);
        }
        input.getProperty().setBold(false);
        input.getProperty().setItalic(false);
        input.getProperty().setUnderLineSort(UnderLineSort.None);
        input.getProperty().setStrikeLine(false);
        shapes.add(input);
        return shapes.size() - 1;
    }

    private static int firstShapeId(Paragraph paragraph, int shapeCount) {
        ParaCharShape runs = paragraph.getCharShape();
        if (runs == null || runs.getPositonShapeIdPairList().isEmpty()) {
            return -1;
        }
        long id = runs.getPositonShapeIdPairList().getFirst().getShapeId();
        return id < 0 || id >= shapeCount ? -1 : (int) id;
    }

    /** The text size most cells of a table use (in 1/100 pt), or 0 when unknown. */
    static int typicalSize(HWPFile file, Iterable<Cell> cells) {
        List<CharShape> charShapes = file.getDocInfo().getCharShapeList();
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (Cell cell : cells) {
            for (Paragraph paragraph : cell.getParagraphList()) {
                ParaCharShape shapes = paragraph.getCharShape();
                if (paragraph.getText() == null || shapes == null || shapes.getPositonShapeIdPairList().isEmpty()) {
                    continue;
                }
                long id = shapes.getPositonShapeIdPairList().getFirst().getShapeId();
                if (id >= 0 && id < charShapes.size()) {
                    counts.merge(charShapes.get((int) id).getBaseSize(), 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse(0);
    }

    static ParaText text(Paragraph paragraph) {
        if (paragraph.getText() == null) {
            paragraph.createText();
        }
        return paragraph.getText();
    }
}
