package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.CtrlHeaderGso;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.HeightCriterion;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.HorzRelTo;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.ObjectNumberSort;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.RelativeArrange;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.TextFlowMethod;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.TextHorzArrange;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.VertRelTo;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.WidthCriterion;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlPicture;
import kr.dogfoot.hwplib.object.bodytext.control.gso.GsoControlType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.ShapeComponentNormal;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponenteach.ShapeComponentPicture;
import kr.dogfoot.hwplib.object.bodytext.control.gso.textbox.TextVerticalAlignment;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Table;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.ParaText;
import kr.dogfoot.hwplib.object.docinfo.BinData;
import kr.dogfoot.hwplib.object.docinfo.ParaShape;
import kr.dogfoot.hwplib.object.docinfo.parashape.Alignment;
import kr.dogfoot.hwplib.object.docinfo.bindata.BinDataCompress;
import kr.dogfoot.hwplib.object.docinfo.bindata.BinDataState;
import kr.dogfoot.hwplib.object.docinfo.bindata.BinDataType;
import kr.dogfoot.hwplib.object.docinfo.borderfill.fillinfo.PictureEffect;

final class CellImageInserter {
    private static final BinDataCompress COMPRESSION = BinDataCompress.ByStorageDefault;
    /** About 0.1mm per side so rounding never overflows the cell. */
    private static final long SAFETY = 60;
    private static final long HWP_UNITS_PER_PIXEL = 75;

    private CellImageInserter() {}

    static void insert(HWPFile file, Table table, Cell cell, Path image) throws IOException {
        String extension = extension(image);
        int[] pixels = pixelSize(image);
        PhotoPlacement placement = PhotoPlacement.contain(
                pixels[0], pixels[1], innerWidth(table, cell), innerHeight(table, cell));
        int streamIndex = file.getBinData().getEmbeddedBinaryDataList().size() + 1;
        String streamName = "Bin%04X.%s".formatted(streamIndex, extension);
        file.getBinData().addNewEmbeddedBinaryData(streamName, Files.readAllBytes(image), COMPRESSION);

        BinData binData = new BinData();
        binData.getProperty().setType(BinDataType.Embedding);
        binData.getProperty().setCompress(COMPRESSION);
        binData.getProperty().setState(BinDataState.NotAccess);
        binData.setBinDataID(streamIndex);
        binData.setExtensionForEmbedding(extension);
        file.getDocInfo().getBinDataList().add(binData);
        int binDataId = file.getDocInfo().getBinDataList().size();

        // The slot label ("사진1") would otherwise show around the photo.
        Paragraph paragraph = CellContent.clear(cell, false);
        // Treated as a character in a centered paragraph of a vertically centered cell — the way
        // a person centers a photo in a table cell in 한글.
        cell.getListHeader().getProperty().setTextVerticalAlignment(TextVerticalAlignment.Center);
        centerParagraph(file, paragraph);
        ParaText text = CellContent.text(paragraph);
        text.addExtendCharForGSO();
        // A real picture object ("그림"), not a rectangle filled with the image: 한글 shows both,
        // but other renderers (rhwp, used for PDF) only draw pictures reliably.
        ControlPicture picture = (ControlPicture) paragraph.addNewGsoControl(GsoControlType.Picture);
        configureHeader(picture.getHeader(), placement, streamIndex);
        configureShape((ShapeComponentNormal) picture.getShapeComponent(), placement.width(), placement.height());
        configurePicture(picture.getShapeComponentPicture(), placement, pixels, binDataId, streamIndex);
        // One line exactly as tall as the photo. 한글 recomputes a missing line layout, but rhwp (PDF)
        // lays out inline pictures from it and silently drops the photo without one.
        writeLine(paragraph, placement, innerWidth(table, cell));
    }

    private static void writeLine(Paragraph paragraph, PhotoPlacement placement, long width) {
        paragraph.deleteLineSeg();
        paragraph.createLineSeg();
        var line = paragraph.getLineSeg().addNewLineSegItem();
        int height = Math.toIntExact(placement.height());
        line.setTextStartPosition(0);
        line.setLineVerticalPosition(0);
        line.setLineHeight(height);
        line.setTextPartHeight(height);
        line.setDistanceBaseLineToLineVerticalPosition(Math.round(height * 0.85f));
        line.setLineSpace(0);
        line.setStartPositionFromColumn(0);
        line.setSegmentWidth(Math.toIntExact(width));
        line.getTag().setFirstSegmentAtLine(true);
        line.getTag().setLastSegmentAtLine(true);
    }

    private static String extension(Path image) {
        String name = image.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException("사진 파일에 확장자가 없습니다.");
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png")) {
            throw new IllegalArgumentException("JPEG 또는 PNG 사진만 사용할 수 있습니다.");
        }
        return extension;
    }

    private static void centerParagraph(HWPFile file, Paragraph paragraph) {
        var paraShapes = file.getDocInfo().getParaShapeList();
        int current = paragraph.getHeader().getParaShapeId();
        ParaShape centered = current >= 0 && current < paraShapes.size()
                ? paraShapes.get(current).clone()
                : file.getDocInfo().addNewParaShape();
        centered.getProperty1().setAlignment(Alignment.Center);
        centered.setLeftMargin(0);
        centered.setRightMargin(0);
        centered.setIndent(0);
        if (!paraShapes.contains(centered)) {
            paraShapes.add(centered);
        }
        paragraph.getHeader().setParaShapeId(paraShapes.indexOf(centered));
    }

    private static int[] pixelSize(Path image) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(image.toFile())) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("사진 크기를 읽지 못했습니다.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private static long innerWidth(Table table, Cell cell) {
        var header = cell.getListHeader();
        long margins = header.getProperty().isApplyInnerMagin()
                ? header.getLeftMargin() + header.getRightMargin()
                : table.getLeftInnerMargin() + table.getRightInnerMargin();
        return header.getWidth() - margins - SAFETY;
    }

    private static long innerHeight(Table table, Cell cell) {
        var header = cell.getListHeader();
        long margins = header.getProperty().isApplyInnerMagin()
                ? header.getTopMargin() + header.getBottomMargin()
                : table.getTopInnerMargin() + table.getBottomInnerMargin();
        return header.getHeight() - margins - SAFETY;
    }

    private static void configureHeader(
            CtrlHeaderGso header, PhotoPlacement placement, int streamIndex) {
        var property = header.getProperty();
        property.setLikeWord(true);
        property.setApplyLineSpace(false);
        property.setVertRelTo(VertRelTo.Para);
        property.setVertRelativeArrange(RelativeArrange.TopOrLeft);
        property.setHorzRelTo(HorzRelTo.Para);
        property.setHorzRelativeArrange(RelativeArrange.TopOrLeft);
        property.setVertRelToParaLimit(true);
        property.setAllowOverlap(true);
        property.setWidthCriterion(WidthCriterion.Absolute);
        property.setHeightCriterion(HeightCriterion.Absolute);
        property.setProtectSize(false);
        // In front of text: the photo never pushes the row taller than the form designed it.
        property.setTextFlowMethod(TextFlowMethod.InFrontOfText);
        property.setTextHorzArrange(TextHorzArrange.BothSides);
        property.setObjectNumberSort(ObjectNumberSort.Figure);
        header.setyOffset(0);
        header.setxOffset(0);
        header.setWidth(placement.width());
        header.setHeight(placement.height());
        header.setzOrder(0);
        header.setInstanceId(0x5bb840e1 + streamIndex);
        header.setPreventPageDivide(false);
        header.getExplanation().setBytes(null);
    }

    private static void configureShape(ShapeComponentNormal shape, long width, long height) {
        int shapeWidth = Math.toIntExact(width);
        int shapeHeight = Math.toIntExact(height);
        shape.getProperty().setRotateWithImage(true);
        shape.setOffsetX(0);
        shape.setOffsetY(0);
        shape.setGroupingCount(0);
        shape.setLocalFileVersion(1);
        shape.setWidthAtCreate(shapeWidth);
        shape.setHeightAtCreate(shapeHeight);
        shape.setWidthAtCurrent(shapeWidth);
        shape.setHeightAtCurrent(shapeHeight);
        shape.setRotateAngle(0);
        shape.setRotateXCenter(shapeWidth / 2);
        shape.setRotateYCenter(shapeHeight / 2);
        shape.setMatrixsNormal();
    }

    private static void configurePicture(
            ShapeComponentPicture picture, PhotoPlacement placement, int[] pixels, int binDataId, int streamIndex) {
        int width = Math.toIntExact(placement.width());
        int height = Math.toIntExact(placement.height());
        // The image's own size in HWP units (96 dpi → 75 units per pixel); the crop keeps all of it.
        long imageWidth = pixels[0] * HWP_UNITS_PER_PIXEL;
        long imageHeight = pixels[1] * HWP_UNITS_PER_PIXEL;
        picture.getBorderColor().setValue(0);
        picture.setBorderThickness(0);
        picture.getBorderProperty().setLineType(LineType.None);
        picture.getLeftTop().setX(0);
        picture.getLeftTop().setY(0);
        picture.getRightTop().setX(width);
        picture.getRightTop().setY(0);
        picture.getRightBottom().setX(width);
        picture.getRightBottom().setY(height);
        picture.getLeftBottom().setX(0);
        picture.getLeftBottom().setY(height);
        picture.setLeftAfterCutting(0);
        picture.setTopAfterCutting(0);
        picture.setRightAfterCutting(Math.toIntExact(imageWidth));
        picture.setBottomAfterCutting(Math.toIntExact(imageHeight));
        picture.getInnerMargin().setLeft(0);
        picture.getInnerMargin().setRight(0);
        picture.getInnerMargin().setTop(0);
        picture.getInnerMargin().setBottom(0);
        picture.getPictureInfo().setBrightness((byte) 0);
        picture.getPictureInfo().setContrast((byte) 0);
        picture.getPictureInfo().setEffect(PictureEffect.RealPicture);
        picture.getPictureInfo().setBinItemID(binDataId);
        picture.setBorderTransparency((short) 0);
        picture.setInstanceId(0x5bb840e1L + streamIndex);
        picture.setImageWidth(imageWidth);
        picture.setImageHeight(imageHeight);
    }
}
