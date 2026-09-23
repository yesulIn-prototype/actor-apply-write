package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhotoPlacementTest {
    @Test
    @DisplayName("세로 사진을 가로로 넓은 칸에 넣으면 높이를 꽉 채운다")
    void portraitInWideCellFillsHeight() {
        PhotoPlacement placement = PhotoPlacement.contain(3_000, 4_000, 20_000, 10_000);

        assertThat(placement).isEqualTo(new PhotoPlacement(7_500, 10_000));
    }

    @Test
    @DisplayName("가로 사진을 세로로 긴 칸에 넣으면 너비를 꽉 채운다")
    void landscapeInTallCellFillsWidth() {
        PhotoPlacement placement = PhotoPlacement.contain(4_000, 3_000, 24_000, 30_000);

        assertThat(placement).isEqualTo(new PhotoPlacement(24_000, 18_000));
    }

    @Test
    @DisplayName("비율이 같으면 칸 전체를 채운다")
    void sameRatioFillsWholeCell() {
        PhotoPlacement placement = PhotoPlacement.contain(1_200, 1_600, 24_000, 32_000);

        assertThat(placement).isEqualTo(new PhotoPlacement(24_000, 32_000));
    }

    @Test
    @DisplayName("사진 비율은 칸 모양과 상관없이 유지된다")
    void keepsAspectRatio() {
        PhotoPlacement placement = PhotoPlacement.contain(1_080, 1_920, 26_010, 31_462);

        double photoRatio = 1_080.0 / 1_920;
        double placedRatio = (double) placement.width() / placement.height();
        assertThat(placedRatio).isCloseTo(photoRatio, org.assertj.core.data.Offset.offset(0.001));
        assertThat(placement.height()).isEqualTo(31_462);
    }
}
