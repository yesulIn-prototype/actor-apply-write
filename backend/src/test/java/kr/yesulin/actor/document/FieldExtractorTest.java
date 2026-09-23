package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.yesulin.actor.document.FieldCandidate.FieldKind;
import kr.yesulin.actor.document.FieldCandidate.InputStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regression tests against real audition forms. Add a form here whenever one is parsed wrongly. */
class FieldExtractorTest {
    private final FieldExtractor extractor = new FieldExtractor();

    @Test
    @DisplayName("공부의신: 빈칸·안내문구·사진 4장을 찾고 경력사항은 여러 줄로 받는다")
    void gongbu() throws Exception {
        List<FieldCandidate> fields = fields("application.hwp");

        assertThat(labels(fields, FieldKind.TEXT)).contains(
                "이름", "생년월일", "주소", "연락처 (집)", "연락처 (핸드폰)", "E-mail", "희망배역", "최종학력",
                "키/몸무게", "취미", "특기", "SNS 주소", "그 외 자격사항 및 간단한 자기소개");
        assertThat(labels(fields, FieldKind.PHOTO)).containsExactly("사진1", "사진2", "사진3", "사진4");
        assertThat(field(fields, "생년월일").style()).isEqualTo(InputStyle.GUIDE);
        assertThat(field(fields, "지방공연 가능 여부").style()).isEqualTo(InputStyle.TEMPLATE);
        assertThat(field(fields, "SNS 주소").hint()).isEqualTo("(인스타그램, 틱톡 등/ 비공개 제외)");
        FieldCandidate career = field(fields, "경력사항 ( 작품 / 역할 / 공연장 / 공연연도 )");
        assertThat(career.address()).isEqualTo(new CellAddress(0, 10, 0));
        assertThat(career.multiline()).isTrue();
        assertThat(career.style()).isEqualTo(InputStyle.GUIDE);
    }

    @Test
    @DisplayName("더스테이지 2026: 0000년 서식은 안내문구, 라벨 칸은 비워두고, 작성 요청 칸은 답으로 바꾼다")
    void stage2026() throws Exception {
        List<FieldCandidate> fields = fields("stage2026.hwp");

        FieldCandidate birth = field(fields, "생년월일");
        assertThat(birth.address()).isEqualTo(new CellAddress(0, 1, 1));
        assertThat(birth.style()).isEqualTo(InputStyle.GUIDE);
        assertThat(fields).noneMatch(field -> field.address().equals(new CellAddress(0, 1, 0)));
        assertThat(field(fields, "성별").style()).isEqualTo(InputStyle.TEMPLATE);
        FieldCandidate motive = field(fields, "지원동기");
        assertThat(motive.style()).isEqualTo(InputStyle.GUIDE);
        assertThat(motive.multiline()).isTrue();
    }

    @Test
    @DisplayName("tf: 사진 칸 4개, 콜론 뒤에 쓰는 지원 배역, @ 안내칸")
    void tf() throws Exception {
        List<FieldCandidate> fields = fields("tf.hwp");

        assertThat(fields).filteredOn(field -> field.kind() == FieldKind.PHOTO).hasSize(4);
        assertThat(field(fields, "지원 배역(중복 가능)").style()).isEqualTo(InputStyle.TEMPLATE);
        FieldCandidate sns = field(fields, "SNS(인스타만)");
        assertThat(sns.style()).isEqualTo(InputStyle.GUIDE);
        assertThat(sns.currentText().strip()).isEqualTo("@");
    }

    @Test
    @DisplayName("지금우리는: '프로필 사진' 머리칸 아래 두 칸이 사진 자리다")
    void jigeum() throws Exception {
        assertThat(labels(fields("jigeum.hwp"), FieldKind.PHOTO)).containsExactly("프로필 사진 1", "프로필 사진 2");
    }

    @Test
    @DisplayName("이미 작성된 지원서: 기존 답은 고칠 값으로 가져오고 라벨 칸에는 덧붙이지 않는다")
    void filledForm() throws Exception {
        List<FieldCandidate> fields = fields("gongbu-filled.hwp");

        FieldCandidate name = field(fields, "이름");
        assertThat(name.style()).isEqualTo(InputStyle.FILLED);
        assertThat(name.currentText().strip()).isEqualTo("홍길동");
        assertThat(field(fields, "연락처 2").currentText().strip()).isEqualTo("010-0000-0000");
        assertThat(fields).noneMatch(field -> field.style() == InputStyle.APPEND);
        assertThat(labels(fields, FieldKind.PHOTO)).containsExactly("사진 1", "사진 2", "사진 3", "사진 4");
        // A blank form is never mistaken for a filled one.
        assertThat(fields("application.hwp")).noneMatch(field -> field.style() == InputStyle.FILLED);
        assertThat(fields("estc.hwp")).noneMatch(field -> field.style() == InputStyle.FILLED);
    }

    @Test
    @DisplayName("한강대: 세로로 합친 칸은 묶음 이름이 되고, 다음 쪽으로 이어진 표는 번호를 이어 붙인다")
    void hangang() throws Exception {
        List<FieldCandidate> fields = fields("hangang.hwp");

        FieldCandidate first = field(fields, "희망 근무 타임 1순위");
        assertThat(first.group()).isEqualTo("희망 근무 타임");
        assertThat(first.column()).isEqualTo("1순위");
        assertThat(fields).filteredOn(field -> field.group().equals("희망 근무 타임"))
                .extracting(FieldCandidate::column).containsExactly("1순위", "2순위", "3순위");

        List<FieldCandidate> career = fields.stream().filter(field -> field.group().equals("경력사항")).toList();
        // 5 rows on page 1, 6 more in the table continued on page 2: 11 rows, one "공연기간" each.
        assertThat(career).filteredOn(field -> field.column().equals("공연기간"))
                .extracting(FieldCandidate::row).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
    }

    @Test
    @DisplayName("ESTC: 서식칸은 미리 채우고, 이름칸에 여백을 둔 라벨과 학력 표를 구분한다")
    void estc() throws Exception {
        List<FieldCandidate> fields = fields("estc.hwp");

        assertThat(field(fields, "성명(한글)").style()).isEqualTo(InputStyle.APPEND);
        assertThat(field(fields, "생년월일").address()).isEqualTo(new CellAddress(0, 1, 1));
        assertThat(field(fields, "생년월일").hint()).isEqualTo("(년도만적어도좋음)");
        assertThat(field(fields, "성별").style()).isEqualTo(InputStyle.TEMPLATE);
        assertThat(field(fields, "주소 (우편번호포함)").style()).isEqualTo(InputStyle.GUIDE);
        assertThat(field(fields, "병역 /혈액형").style()).isEqualTo(InputStyle.TEMPLATE);
        assertThat(field(fields, "취미/특기/결혼").style()).isEqualTo(InputStyle.TEMPLATE);
        assertThat(field(fields, "외국어 1 / 2").multiline()).isTrue();
        assertThat(labels(fields, FieldKind.PHOTO)).containsExactly("사진");

        List<FieldCandidate> education = fields.stream().filter(field -> field.group().equals("학력")).toList();
        assertThat(education).hasSize(20);
        assertThat(education).extracting(FieldCandidate::rowName).containsOnly("초등학교", "중학교", "고등학교", "대학교", "대학원");
        assertThat(field(fields, "초등학교 출신학교").style()).isEqualTo(InputStyle.TEMPLATE);
        assertThat(field(fields, "대학원 전공").column()).isEqualTo("전공");

        FieldCandidate career = field(fields, "경력 (상세히...)");
        assertThat(career.address()).isEqualTo(new CellAddress(0, 15, 1));
        assertThat(career.multiline()).isTrue();
        assertThat(career.style()).isEqualTo(InputStyle.GUIDE);
        assertThat(field(fields, "자기소개글 (추가 사진들은 자기소개글 다음 페이지에 소개-자유형식)").multiline()).isTrue();
        assertThat(labels(fields, FieldKind.TEXT)).noneMatch(label -> label.contains("중학교 1") || label.startsWith("학력"));
    }

    @Test
    @DisplayName("생활연기: 비워 둘 칸은 빼고, 번호 열이 있는 공연경력 표를 행으로 묶는다")
    void life() throws Exception {
        List<FieldCandidate> fields = fields("life.hwp");

        assertThat(labels(fields, FieldKind.TEXT)).doesNotContain("접수번호");
        assertThat(field(fields, "영상링크(필수)").style()).isEqualTo(InputStyle.GUIDE);
        assertThat(field(fields, "신체조건").style()).isEqualTo(InputStyle.TEMPLATE);
        assertThat(labels(fields, FieldKind.PHOTO)).containsExactly("PHOTO");

        List<FieldCandidate> career = fields.stream().filter(field -> field.group().equals("공연경력")).toList();
        assertThat(career).hasSize(55);
        assertThat(career).extracting(FieldCandidate::column).doesNotContain("No.");
        assertThat(field(fields, "공연명 1").row()).isEqualTo(1);
        assertThat(field(fields, "역할 11").row()).isEqualTo(11);
    }

    @Test
    @DisplayName("하츄핑: 접수용 NO.는 빼고, 선택형 서식과 사진 두 칸을 찾는다")
    void hachu() throws Exception {
        List<FieldCandidate> fields = fields("hachu.hwp");

        assertThat(labels(fields, FieldKind.TEXT)).doesNotContain("NO.");
        assertThat(field(fields, "성별").style()).isEqualTo(InputStyle.TEMPLATE);
        assertThat(field(fields, "음역").style()).isEqualTo(InputStyle.TEMPLATE);
        assertThat(field(fields, "신장").style()).isEqualTo(InputStyle.GUIDE);
        assertThat(field(fields, "공연기간 1").style()).isEqualTo(InputStyle.GUIDE);
        assertThat(field(fields, "공연기간 1").group()).isEqualTo("경력사항");
        assertThat(labels(fields, FieldKind.PHOTO)).containsExactly("사진", "상반신 사진", "전신 사진");
    }

    @Test
    @DisplayName("안내 문구·서식·라벨을 구분한다")
    void classifiesCellText() {
        assertThat(CellText.role("년    월   일")).isEqualTo(CellText.Role.GUIDE);
        assertThat(CellText.role("cm  /    kg")).isEqualTo(CellText.Role.GUIDE);
        assertThat(CellText.role("(집)")).isEqualTo(CellText.Role.GUIDE);
        assertThat(CellText.role("00.00.00~00.00.00")).isEqualTo(CellText.Role.GUIDE);
        assertThat(CellText.role("(구체주소를 밝히고 싶지 않으면 동까지만 적어도 좋음)")).isEqualTo(CellText.Role.GUIDE);
        assertThat(CellText.role("취미:             /특기:        ")).isEqualTo(CellText.Role.TEMPLATE);
        assertThat(CellText.role("(                ) 초등학교")).isEqualTo(CellText.Role.TEMPLATE);
        assertThat(CellText.role("남  /  여")).isEqualTo(CellText.Role.TEMPLATE);
        assertThat(CellText.role("(공란으로 비워 주시기 바랍니다)")).isEqualTo(CellText.Role.SKIP);
        assertThat(CellText.role("이름--한문 영문")).isEqualTo(CellText.Role.LABEL);
        assertThat(CellText.role("경력 (상세히...)")).isEqualTo(CellText.Role.LABEL);
        assertThat(CellText.role("일시")).isEqualTo(CellText.Role.LABEL);
        assertThat(CellText.normalize("이   름")).isEqualTo("이름");
        assertThat(CellText.normalize("자 기 소 개 글 (자유형식)")).isEqualTo("자기소개글 (자유형식)");
        assertThat(CellText.normalize("그 외 자격사항")).isEqualTo("그 외 자격사항");
    }

    @Test
    @DisplayName("이름 옆의 큰 빈칸은 사진 문구가 없어도 사진 슬롯으로 찾는다")
    void extract_FindsUnlabeledPortraitCell_NextToIdentityFields() {
        List<CellSnapshot> cells = List.of(
                new CellSnapshot(new CellAddress(0, 1, 0), 0, 1, 3, 5, 1273, 4535, ""),
                new CellSnapshot(new CellAddress(0, 1, 1), 3, 1, 3, 1, 8958, 4535, "이름"),
                new CellSnapshot(new CellAddress(0, 1, 2), 6, 1, 2, 1, 8958, 4535, ""));

        List<FieldCandidate> fields = extractor.extract(cells);

        assertThat(fields).filteredOn(field -> field.kind() == FieldKind.PHOTO)
                .extracting(FieldCandidate::address).containsExactly(new CellAddress(0, 1, 0));
    }

    @Test
    @DisplayName("여러 줄 값은 줄바꿈을 유지하고 칸에 있던 안내 문구는 모두 지운다")
    void multilineValueReplacesGuideText() throws Exception {
        HwpDocument document = HwpDocument.open(fixture("application.hwp"));
        CellAddress career = new CellAddress(0, 10, 0);
        Path output = Files.createTempFile("yesulin-multiline-", ".hwp");

        document.setText(career, "햄릿 / 햄릿 / 대학로 / 2025\n갈매기 / 트레플레프 / 아르코 / 2024");
        document.save(output);

        String text = cellText(output, career);
        assertThat(text).contains("햄릿 / 햄릿 / 대학로 / 2025", "갈매기 / 트레플레프 / 아르코 / 2024");
        assertThat(text).doesNotContain("학교작품");
    }

    @Test
    @DisplayName("여백형 라벨은 라벨을 남기고 값을 아래에 덧붙이고, 여러 문단 서식은 문단별로 되돌린다")
    void appendKeepsLabelAndTemplateKeepsParagraphs() throws Exception {
        HwpDocument document = HwpDocument.open(fixture("estc.hwp"));
        CellAddress name = new CellAddress(0, 0, 1);
        CellAddress language = new CellAddress(0, 8, 1);
        Path output = Files.createTempFile("yesulin-append-", ".hwp");

        document.appendText(name, "홍길동");
        document.setText(language, "외국어 1: ( 영어 ) 독해 -- 상\n외국어 2: ( 일본어 ) 독해 -- 중");
        document.save(output);

        assertThat(cellText(output, name)).contains("성명(한글)", "홍길동");
        assertThat(cellText(output, language)).contains("외국어 1: ( 영어 )", "외국어 2: ( 일본어 )");
    }

    private List<FieldCandidate> fields(String name) throws Exception {
        return extractor.extract(HwpDocument.open(fixture(name)).cells());
    }

    private static List<String> labels(List<FieldCandidate> fields, FieldKind kind) {
        return fields.stream().filter(field -> field.kind() == kind).map(FieldCandidate::label).toList();
    }

    private static FieldCandidate field(List<FieldCandidate> fields, String label) {
        return fields.stream().filter(field -> field.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no field " + label + " in " + labels(fields, FieldKind.TEXT)));
    }

    private static String cellText(Path hwp, CellAddress address) throws Exception {
        return HwpDocument.open(hwp).cells().stream()
                .filter(cell -> cell.address().equals(address))
                .findFirst()
                .orElseThrow()
                .text();
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(FieldExtractorTest.class.getResource("/fixtures/" + name).toURI());
    }
}
