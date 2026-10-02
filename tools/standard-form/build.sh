#!/usr/bin/env bash
# Builds the 예술in standard application form (standard-v1.hwp) from standard-v1.spec.json.
# Needs tools/rhwp (tools/install-rhwp.sh) and Java. Run from the repository root:
#   bash tools/standard-form/build.sh
set -euo pipefail
cd "$(dirname "$0")"
R=../rhwp/rhwp/rhwp
[ -x "$R" ] || R=$R.exe
JAR=../../backend/libs/hwplib-1.1.11.jar
OUT=../../backend/src/main/resources/standard/standard-v1.hwp
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mm() { for x in "$@"; do printf '%s,' $(( (x * 2834650 + 50000) / 100000 )); done | sed 's/,$//'; }

"$R" scaffold standard-v1.spec.json -o "$WORK/form.hwpx" >/dev/null
"$R" convert "$WORK/form.hwpx" "$WORK/0.hwp" --verify >/dev/null
# Column widths in 0.1 mm: 인적사항, 지원 정보, 출연 경력, 프로필 사진 2장 (148 mm wide each).
"$R" edit set-column-widths "$WORK/0.hwp" --table 0 --widths "$(mm 190 330 190 370 400)" -o "$WORK/1.hwp" >/dev/null
"$R" edit set-column-widths "$WORK/1.hwp" --table 1 --widths "$(mm 400 1080)" -o "$WORK/2.hwp" >/dev/null
"$R" edit set-column-widths "$WORK/2.hwp" --table 2 --widths "$(mm 400 220 280 320 260)" -o "$WORK/3.hwp" >/dev/null
"$R" edit set-column-widths "$WORK/3.hwp" --table 3 --widths "$(mm 740 740)" -o "$WORK/4.hwp" >/dev/null
# 대표 사진 (rows 0-5 of the last column, 40 x 54 mm = 3:4), 학력·특기 value cells.
"$R" edit merge-cells "$WORK/4.hwp" --table 0 --row 0 --col 4 --end-row 5 --end-col 4 -o "$WORK/5.hwp" >/dev/null
"$R" edit merge-cells "$WORK/5.hwp" --table 0 --row 4 --col 1 --end-row 4 --end-col 3 -o "$WORK/6.hwp" >/dev/null
"$R" edit merge-cells "$WORK/6.hwp" --table 0 --row 5 --col 1 --end-row 5 --end-col 3 -o "$WORK/7.hwp" >/dev/null
# 출연 경력 and 프로필 사진 start page 2 (paragraph 8 is the "3. 출연 경력" heading).
"$R" edit insert-page-break "$WORK/7.hwp" --para 8 --offset 0 -o "$WORK/8.hwp" >/dev/null
# scaffold writes 1 mm rows; give them the heights of a form made in Hangul (mm).
java -cp "$JAR" Heights.java "$WORK/8.hwp" "$OUT" \
  9,9,9,9,9,9 9.5,9.5,9.5,55 8,8,8,8,8,8,8,8,8,8,8 98.5
echo "built $OUT"
