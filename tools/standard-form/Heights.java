import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.writer.HWPWriter;
import kr.dogfoot.hwplib.object.bodytext.control.*;
import kr.dogfoot.hwplib.tool.objectfinder.ControlFinder;
/** Rewrites row heights (mm) per table: args = in out "9,9,9" "9,60" ... (one list per table, in order). */
public class Heights {
  public static void main(String[] a) throws Exception {
    var f = HWPReader.fromFile(a[0]);
    var tables = ControlFinder.find(f, (c,p,s) -> c.getType()==ControlType.Table);
    for (int ti = 0; ti < tables.size() && ti + 2 < a.length; ti++) {
      var t = (ControlTable) tables.get(ti);
      String[] parts = a[ti + 2].split(",");
      long[] rows = new long[parts.length];
      long total = 0;
      for (int i = 0; i < parts.length; i++) { rows[i] = Math.round(Double.parseDouble(parts[i]) * 283.465); total += rows[i]; }
      for (var r : t.getRowList()) for (var c : r.getCellList()) {
        var h = c.getListHeader(); long sum = 0;
        for (int i = h.getRowIndex(); i < h.getRowIndex() + h.getRowSpan(); i++) sum += rows[i];
        h.setHeight(sum);
      }
      t.getHeader().setHeight(total);
    }
    HWPWriter.toFile(f, a[1]);
  }
}
