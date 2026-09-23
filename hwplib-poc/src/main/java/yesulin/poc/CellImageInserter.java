package yesulin.poc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlRectangle;
import kr.dogfoot.hwplib.object.bodytext.control.gso.GsoControlType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.ShapeComponentNormal;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineArrowShape;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineArrowSize;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineEndShape;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineInfo;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.OutlineStyle;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.shadowinfo.ShadowInfo;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.shadowinfo.ShadowType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponenteach.ShapeComponentRectangle;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.ParaText;
import kr.dogfoot.hwplib.object.docinfo.BinData;
import kr.dogfoot.hwplib.object.docinfo.bindata.BinDataCompress;
import kr.dogfoot.hwplib.object.docinfo.bindata.BinDataState;
import kr.dogfoot.hwplib.object.docinfo.bindata.BinDataType;
import kr.dogfoot.hwplib.object.docinfo.borderfill.fillinfo.FillInfo;
import kr.dogfoot.hwplib.object.docinfo.borderfill.fillinfo.ImageFill;
import kr.dogfoot.hwplib.object.docinfo.borderfill.fillinfo.ImageFillType;
import kr.dogfoot.hwplib.object.docinfo.borderfill.fillinfo.PictureEffect;

final class CellImageInserter {
    private static final BinDataCompress COMPRESSION = BinDataCompress.ByStorageDefault;

    private CellImageInserter() {}

    static void insert(HWPFile file, Cell cell, Path image) throws IOException {
        String extension = extension(image);
        int streamIndex = file.getBinData().getEmbeddedBinaryDataList().size() + 1;
        String streamName = "Bin%04X.%s".formatted(streamIndex, extension);
        file.getBinData().addNewEmbeddedBinaryData(
                streamName, Files.readAllBytes(image), COMPRESSION);

        BinData binData = new BinData();
        binData.getProperty().setType(BinDataType.Embedding);
        binData.getProperty().setCompress(COMPRESSION);
        binData.getProperty().setState(BinDataState.NotAccess);
        binData.setBinDataID(streamIndex);
        binData.setExtensionForEmbedding(extension);
        file.getDocInfo().getBinDataList().add(binData);
        int binDataId = file.getDocInfo().getBinDataList().size();

        Paragraph paragraph = firstParagraph(cell);
        ParaText text = paragraph.getText();
        if (text == null) {
            paragraph.createText();
            text = paragraph.getText();
        }
        text.addExtendCharForGSO();
        ControlRectangle rectangle =
                (ControlRectangle) paragraph.addNewGsoControl(GsoControlType.Rectangle);

        long width = Math.max(1, cell.getListHeader().getWidth());
        long height = Math.max(1, cell.getListHeader().getHeight());
        configureHeader(rectangle.getHeader(), width, height, streamIndex);
        configureShape((ShapeComponentNormal) rectangle.getShapeComponent(), width, height, binDataId);
        configureRectangle(rectangle.getShapeComponentRectangle(), width, height);
        paragraph.deleteLineSeg();
    }

    private static String extension(Path image) {
        String name = image.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException("Image file must have an extension: " + image);
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Paragraph firstParagraph(Cell cell) {
        if (cell.getParagraphList().getParagraphCount() == 0) {
            return cell.getParagraphList().addNewParagraph();
        }
        return cell.getParagraphList().getParagraph(0);
    }

    private static void configureHeader(
            CtrlHeaderGso header, long width, long height, int streamIndex) {
        var property = header.getProperty();
        property.setLikeWord(false);
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
        property.setTextFlowMethod(TextFlowMethod.FitWithText);
        property.setTextHorzArrange(TextHorzArrange.BothSides);
        property.setObjectNumberSort(ObjectNumberSort.Figure);
        header.setyOffset(1);
        header.setxOffset(1);
        header.setWidth(width);
        header.setHeight(height);
        header.setzOrder(0);
        header.setOutterMarginLeft(0);
        header.setOutterMarginRight(0);
        header.setOutterMarginTop(0);
        header.setOutterMarginBottom(0);
        header.setInstanceId(0x5bb840e1 + streamIndex);
        header.setPreventPageDivide(false);
        header.getExplanation().setBytes(null);
    }

    private static void configureShape(
            ShapeComponentNormal shape, long width, long height, int binDataId) {
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

        shape.createLineInfo();
        LineInfo line = shape.getLineInfo();
        line.getProperty().setLineEndShape(LineEndShape.Flat);
        line.getProperty().setStartArrowShape(LineArrowShape.None);
        line.getProperty().setStartArrowSize(LineArrowSize.MiddleMiddle);
        line.getProperty().setEndArrowShape(LineArrowShape.None);
        line.getProperty().setEndArrowSize(LineArrowSize.MiddleMiddle);
        line.getProperty().setFillStartArrow(true);
        line.getProperty().setFillEndArrow(true);
        line.getProperty().setLineType(LineType.None);
        line.setOutlineStyle(OutlineStyle.Normal);
        line.setThickness(0);
        line.getColor().setValue(0);

        shape.createFillInfo();
        FillInfo fill = shape.getFillInfo();
        fill.getType().setPatternFill(false);
        fill.getType().setImageFill(true);
        fill.getType().setGradientFill(false);
        fill.createImageFill();
        ImageFill imageFill = fill.getImageFill();
        imageFill.setImageFillType(ImageFillType.Zoom);
        imageFill.getPictureInfo().setBrightness((byte) 0);
        imageFill.getPictureInfo().setContrast((byte) 0);
        imageFill.getPictureInfo().setEffect(PictureEffect.RealPicture);
        imageFill.getPictureInfo().setBinItemID(binDataId);

        shape.createShadowInfo();
        ShadowInfo shadow = shape.getShadowInfo();
        shadow.setType(ShadowType.None);
        shadow.getColor().setValue(0xc4c4c4);
        shadow.setOffsetX(283);
        shadow.setOffsetY(283);
        shadow.setTransparent((short) 0);
        shape.setMatrixsNormal();
    }

    private static void configureRectangle(
            ShapeComponentRectangle rectangle, long width, long height) {
        int shapeWidth = Math.toIntExact(width);
        int shapeHeight = Math.toIntExact(height);
        rectangle.setRoundRate((byte) 0);
        rectangle.setX1(0);
        rectangle.setY1(0);
        rectangle.setX2(shapeWidth);
        rectangle.setY2(0);
        rectangle.setX3(shapeWidth);
        rectangle.setY3(shapeHeight);
        rectangle.setX4(0);
        rectangle.setY4(shapeHeight);
    }
}
