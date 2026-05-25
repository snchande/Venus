package com.barista.util;

import org.knowm.xchart.*;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.PieStyler;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Arima Notebooks — display helper available in every JShell session.
 *
 * Charts:
 *   XYChart   c = BaristaDisplay.xyChart("Title","X","Y"); c.addSeries(...); BaristaDisplay.show(c);
 *   CategoryChart b = BaristaDisplay.barChart("Title","X","Y"); ...
 *   PieChart  p = BaristaDisplay.pieChart("Title"); ...
 *
 * DataFrame (Tablesaw):
 *   Table df = BaristaDisplay.loadCsv("data/sales.csv");
 *   BaristaDisplay.show(df);          // renders as HTML table (first 50 rows)
 *   BaristaDisplay.show(df, 20);      // first 20 rows
 *   BaristaDisplay.info(df);          // column types + non-null counts
 *   BaristaDisplay.describe(df);      // numeric summary statistics per column
 *
 * Statistics:
 *   BaristaDisplay.stats("label", double[]);
 *   BaristaDisplay.table(String[][], headers...);
 */
public class BaristaDisplay {

    // ── Dark-theme palette (public so notebook cells can reference PALETTE[n]) ──
    private static final Color BG      = new Color(0x0d0d1a);
    private static final Color BG_PLOT = new Color(0x13132a);
    private static final Color FG      = new Color(0xc8d0f0);
    private static final Color FG_DIM  = new Color(0x7a839e);
    private static final Color GRID    = new Color(0x2a2a4a);

    public static final Color[] PALETTE = {
        new Color(0x7c8ef7), new Color(0x6bcf8f), new Color(0xf0a050),
        new Color(0xf07878), new Color(0xa78bfa), new Color(0x38bdf8),
        new Color(0xfbbf24), new Color(0xf472b6)
    };

    // ── Chart factories ─────────────────────────────────────────────────────

    public static XYChart xyChart(String title, String xLabel, String yLabel) {
        return xyChart(title, xLabel, yLabel, 700, 400);
    }
    public static XYChart xyChart(String title, String xLabel, String yLabel, int w, int h) {
        XYChart c = new XYChartBuilder().width(w).height(h)
                .title(title).xAxisTitle(xLabel).yAxisTitle(yLabel).build();
        styleXY(c); return c;
    }

    public static CategoryChart barChart(String title, String xLabel, String yLabel) {
        return barChart(title, xLabel, yLabel, 700, 400);
    }
    public static CategoryChart barChart(String title, String xLabel, String yLabel, int w, int h) {
        CategoryChart c = new CategoryChartBuilder().width(w).height(h)
                .title(title).xAxisTitle(xLabel).yAxisTitle(yLabel).build();
        styleCategory(c); return c;
    }

    public static PieChart pieChart(String title) { return pieChart(title, 640, 420); }
    public static PieChart pieChart(String title, int w, int h) {
        PieChart c = new PieChartBuilder().width(w).height(h).title(title).build();
        stylePie(c); return c;
    }

    // ── show() overloads ────────────────────────────────────────────────────

    public static void show(XYChart chart)       { renderChart(chart); }
    public static void show(CategoryChart chart) { renderChart(chart); }
    public static void show(PieChart chart)      { renderChart(chart); }
    public static void show(BufferedImage img)   { printImage(img); }

    // ── DataFrame (Tablesaw) ─────────────────────────────────────────────────

    /**
     * Load a CSV file and return a Tablesaw Table.
     * Path can be absolute or relative to the Arima working directory.
     */
    public static tech.tablesaw.api.Table loadCsv(String path) {
        try {
            return tech.tablesaw.api.Table.read().csv(path);
        } catch (Exception e) {
            throw new RuntimeException("Could not load CSV: " + e.getMessage(), e);
        }
    }

    /** Display a Tablesaw Table as an HTML table (first 50 rows). */
    public static void show(tech.tablesaw.api.Table table) { show(table, 50); }

    /** Display a Tablesaw Table as an HTML table (first {@code maxRows} rows). */
    public static void show(tech.tablesaw.api.Table table, int maxRows) {
        if (table == null) { System.out.println("(null table)"); return; }
        int rows = Math.min(maxRows, table.rowCount());
        boolean truncated = rows < table.rowCount();

        StringBuilder sb = new StringBuilder("BARISTA_HTML:");
        sb.append("<div class=\"barista-df-wrap\">");
        sb.append("<div class=\"barista-df-meta\">")
          .append(escHtml(table.name())).append("  ")
          .append(table.rowCount()).append(" rows × ")
          .append(table.columnCount()).append(" cols");
        if (truncated) sb.append("  <span class=\"barista-df-trunc\">(showing first ").append(rows).append(")</span>");
        sb.append("</div>");
        sb.append("<table class=\"barista-table barista-df\">");
        sb.append("<thead><tr>");
        for (var col : table.columns()) {
            sb.append("<th>").append(escHtml(col.name()))
              .append("<span class=\"barista-col-type\">").append(escHtml(col.type().name())).append("</span>")
              .append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (int r = 0; r < rows; r++) {
            sb.append("<tr>");
            for (var col : table.columns()) {
                sb.append("<td>").append(escHtml(String.valueOf(col.get(r)))).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        System.out.println(sb);
    }

    /**
     * Print column structure: name, type, missing-count for each column.
     */
    public static void info(tech.tablesaw.api.Table table) {
        if (table == null) return;
        String[][] data = new String[table.columnCount()][3];
        for (int i = 0; i < table.columnCount(); i++) {
            var col = table.column(i);
            data[i][0] = col.name();
            data[i][1] = col.type().name();
            data[i][2] = String.valueOf(col.countMissing());
        }
        System.out.println("Table: " + table.name()
            + "  [" + table.rowCount() + " rows × " + table.columnCount() + " cols]");
        table(data, "Column", "Type", "Missing");
    }

    /**
     * Print descriptive statistics for every numeric column.
     */
    public static void describe(tech.tablesaw.api.Table table) {
        if (table == null) return;
        for (var col : table.columns()) {
            if (col instanceof tech.tablesaw.api.NumericColumn<?> nc) {
                double[] vals = nc.asDoubleArray();
                stats(col.name(), vals);
            }
        }
    }

    // ── Static HTML table ───────────────────────────────────────────────────

    /** Render a 2-D String array as an HTML table with optional column headers. */
    public static void table(String[][] data, String... headers) {
        StringBuilder sb = new StringBuilder("BARISTA_HTML:");
        sb.append("<table class=\"barista-table\">");
        if (headers.length > 0) {
            sb.append("<thead><tr>");
            for (String h : headers) sb.append("<th>").append(escHtml(h)).append("</th>");
            sb.append("</tr></thead>");
        }
        sb.append("<tbody>");
        for (String[] row : data) {
            sb.append("<tr>");
            for (String cell : row) sb.append("<td>").append(escHtml(cell != null ? cell : "")).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        System.out.println(sb);
    }

    /** Print summary statistics for a double array as an HTML table. */
    public static void stats(String label, double[] values) {
        if (values == null || values.length == 0) { System.out.println(label + ": (empty)"); return; }
        double sum = 0, min = values[0], max = values[0];
        for (double v : values) { sum += v; if (v < min) min = v; if (v > max) max = v; }
        double mean = sum / values.length;
        double var  = 0;
        for (double v : values) { double d = v - mean; var += d * d; }
        double stddev = Math.sqrt(var / values.length);
        // Median
        double[] sorted = values.clone(); java.util.Arrays.sort(sorted);
        double median = sorted.length % 2 == 0
            ? (sorted[sorted.length/2 - 1] + sorted[sorted.length/2]) / 2.0
            : sorted[sorted.length/2];
        table(new String[][] {
            {"Count",  String.valueOf(values.length)},
            {"Min",    String.format("%.4g", min)},
            {"Max",    String.format("%.4g", max)},
            {"Mean",   String.format("%.4g", mean)},
            {"Median", String.format("%.4g", median)},
            {"StdDev", String.format("%.4g", stddev)},
        }, label, "Value");
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    @SuppressWarnings("rawtypes")
    private static void renderChart(Chart chart) {
        try { printImage(BitmapEncoder.getBufferedImage(chart)); }
        catch (Exception e) { System.err.println("BaristaDisplay.show() failed: " + e.getMessage()); }
    }

    private static void printImage(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            System.out.println("BARISTA_IMG:data:image/png;base64,"
                + Base64.getEncoder().encodeToString(baos.toByteArray()));
        } catch (Exception e) { System.err.println("BaristaDisplay image error: " + e.getMessage()); }
    }

    // ── Style helpers ─────────────────────────────────────────────────────────

    private static void styleXY(XYChart c) {
        var s = c.getStyler();
        applyBase(s); applyAxes(s);
        s.setSeriesColors(PALETTE); s.setMarkerSize(6);
        s.setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
    }
    private static void styleCategory(CategoryChart c) {
        var s = c.getStyler();
        applyBase(s); applyAxes(s);
        s.setSeriesColors(PALETTE); s.setAvailableSpaceFill(0.7);
    }
    private static void stylePie(PieChart c) {
        var s = c.getStyler();
        applyBase(s);
        s.setSeriesColors(PALETTE); s.setDonutThickness(0.4);
        s.setDefaultSeriesRenderStyle(PieSeries.PieSeriesRenderStyle.Donut);
    }
    private static void applyBase(org.knowm.xchart.style.Styler s) {
        s.setChartBackgroundColor(BG); s.setPlotBackgroundColor(BG_PLOT);
        s.setChartFontColor(FG); s.setPlotBorderVisible(false);
        s.setChartTitleFont(new Font("SansSerif", Font.BOLD, 14));
        s.setLegendBackgroundColor(BG); s.setLegendBorderColor(GRID);
        s.setLegendFont(new Font("SansSerif", Font.PLAIN, 11));
    }
    private static void applyAxes(org.knowm.xchart.style.AxesChartStyler s) {
        s.setAxisTickLabelsColor(FG_DIM); s.setPlotGridLinesColor(GRID);
        s.setAxisTitleFont(new Font("SansSerif", Font.PLAIN, 12));
        s.setAxisTickLabelsFont(new Font("Monospaced", Font.PLAIN, 10));
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
