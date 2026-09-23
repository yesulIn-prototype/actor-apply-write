package kr.yesulin.actor.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GenerateRequest(
        List<@Valid TextValue> textValues,
        List<@Valid PhotoValue> photos,
        String fileName) {

    public GenerateRequest(List<TextValue> textValues, List<PhotoValue> photos) {
        this(textValues, photos, null);
    }

    public GenerateRequest {
        textValues = textValues == null ? List.of() : List.copyOf(textValues);
        photos = photos == null ? List.of() : List.copyOf(photos);
        if (textValues.isEmpty() && photos.isEmpty()) {
            throw new IllegalArgumentException("입력할 텍스트나 사진이 하나 이상 필요합니다.");
        }
    }

    public record TextValue(
            @NotBlank String fieldId,
            @NotNull @Valid CellAddress address,
            @Size(max = 5000) String value) {}

    public record PhotoValue(
            @NotBlank String fieldId,
            @NotNull @Valid CellAddress address,
            @NotBlank String fileKey) {}
}
