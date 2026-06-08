package com.statanalyzer.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.statanalyzer.model.StatResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сервис экспорта результатов анализа.
 * Поддерживает форматы:
 *   Графики: PNG, SVG, PDF, HTML
 *   Таблицы: CSV, XLSX, JSON, MD
 */
public class ExportService {

    public enum ExportType { CHART, TABLE }

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /** Универсальная точка входа для экспорта. */
    public void export(StatResult result, File file, ExportType type, String format) throws Exception {
        if (type == ExportType.CHART) {
            exportChart(result, file, format.toLowerCase());
        } else {
            exportTable(result, file, format.toLowerCase());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Экспорт графиков
    // ─────────────────────────────────────────────────────────────────────────

    private void exportChart(StatResult result, File file, String format) throws Exception {
        switch (format) {
            case "png"  -> saveChartPng(result, file);
            case "svg"  -> saveChartSvg(result, file);
            case "pdf"  -> saveChartPdf(result, file);
            case "html" -> saveChartHtml(result, file);
            default -> throw new IllegalArgumentException("Неизвестный формат графика: " + format);
        }
    }

    private void saveChartPng(StatResult result, File file) throws Exception {
        BufferedImage img = renderHistogramToImage(result, 900, 600);
        ImageIO.write(img, "PNG", file);
    }

    private void saveChartSvg(StatResult result, File file) throws Exception {
        String svg = buildSvg(result, 900, 500);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(svg);
        }
    }

    private void saveChartPdf(StatResult result, File file) throws Exception {
        BufferedImage img = renderHistogramToImage(result, 900, 600);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(900, 600));
            doc.addPage(page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 0, 0, 900, 600);
            }
            doc.save(file);
        }
    }

    private void saveChartHtml(StatResult result, File file) throws Exception {
        String html = buildChartHtml(result);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(html);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Экспорт таблиц
    // ─────────────────────────────────────────────────────────────────────────

    private void exportTable(StatResult result, File file, String format) throws Exception {
        switch (format) {
            case "csv"  -> saveTableCsv(result, file);
            case "xlsx" -> saveTableXlsx(result, file);
            case "json" -> saveTableJson(result, file);
            case "md"   -> saveTableMd(result, file);
            default -> throw new IllegalArgumentException("Неизвестный формат таблицы: " + format);
        }
    }

    private void saveTableCsv(StatResult result, File file) throws Exception {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write("﻿"); // BOM for Excel compatibility
            w.write("Показатель,Значение\n");
            for (Map.Entry<String, String> e : result.getStatisticsMap().entrySet()) {
                w.write(escapeCsv(e.getKey()) + "," + escapeCsv(e.getValue()) + "\n");
            }
        }
    }

    private void saveTableXlsx(StatResult result, File file) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Статистика");
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Показатель");
            header.createCell(1).setCellValue("Значение");
            header.getCell(0).setCellStyle(headerStyle);
            header.getCell(1).setCellStyle(headerStyle);

            int rowIdx = 1;
            for (Map.Entry<String, String> e : result.getStatisticsMap().entrySet()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(e.getKey());
                try {
                    row.createCell(1).setCellValue(Double.parseDouble(e.getValue()));
                } catch (NumberFormatException ex) {
                    row.createCell(1).setCellValue(e.getValue());
                }
            }
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);

            // Лист с частотами
            Sheet histSheet = wb.createSheet("Гистограмма");
            Row hHeader = histSheet.createRow(0);
            hHeader.createCell(0).setCellValue("Интервал (левая граница)");
            hHeader.createCell(1).setCellValue("Интервал (правая граница)");
            hHeader.createCell(2).setCellValue("Частота (абс.)");
            hHeader.createCell(3).setCellValue("Частота (отн.)");

            double[] edges = result.getBinEdges();
            int[]    freq  = result.getFrequencies();
            double[] rel   = result.getRelativeFrequencies();
            for (int i = 0; i < freq.length; i++) {
                Row r = histSheet.createRow(i + 1);
                r.createCell(0).setCellValue(edges[i]);
                r.createCell(1).setCellValue(edges[i + 1]);
                r.createCell(2).setCellValue(freq[i]);
                r.createCell(3).setCellValue(rel[i]);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                wb.write(fos);
            }
        }
    }

    private void saveTableJson(StatResult result, File file) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("column", result.getColumnName());
        data.put("statistics", result.getStatisticsMap());

        Map<String, Object> hist = new LinkedHashMap<>();
        hist.put("numBins", result.getNumBins());
        hist.put("binWidth", result.getBinWidth());
        hist.put("binEdges", result.getBinEdges());
        hist.put("frequencies", result.getFrequencies());
        hist.put("relativeFrequencies", result.getRelativeFrequencies());
        data.put("histogram", hist);

        MAPPER.writeValue(file, data);
    }

    private void saveTableMd(StatResult result, File file) throws Exception {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write("# Статистический анализ: «" + result.getColumnName() + "»\n\n");
            w.write("## Основные показатели\n\n");
            w.write("| Показатель | Значение |\n");
            w.write("|---|---|\n");
            for (Map.Entry<String, String> e : result.getStatisticsMap().entrySet()) {
                w.write("| " + e.getKey() + " | " + e.getValue() + " |\n");
            }
            w.write("\n## Частотная таблица\n\n");
            w.write("| Интервал | Частота | Отн. частота |\n");
            w.write("|---|---|---|\n");
            double[] edges = result.getBinEdges();
            int[] freq = result.getFrequencies();
            double[] rel = result.getRelativeFrequencies();
            for (int i = 0; i < freq.length; i++) {
                w.write(String.format("| [%.4f, %.4f) | %d | %.4f |\n",
                    edges[i], edges[i + 1], freq[i], rel[i]));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные методы рендеринга
    // ─────────────────────────────────────────────────────────────────────────

    /** Рендерит гистограмму в BufferedImage через Java 2D (без JavaFX Canvas). */
    public static BufferedImage renderHistogramToImage(StatResult result, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // Фон
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, width, height);

        int margin = 60;
        int chartW = width - 2 * margin;
        int chartH = height - 2 * margin;

        int[] freq = result.getFrequencies();
        double[] edges = result.getBinEdges();
        int numBins = freq.length;
        if (numBins == 0) { g.dispose(); return img; }

        int maxFreq = 0;
        for (int f : freq) if (f > maxFreq) maxFreq = f;

        // Учитываем высоту кривой нормального распределения
        double maxYVal = maxFreq;
        if (result.isShowNormalCurve() && result.getNormalCurveY().length > 0) {
            for (double y : result.getNormalCurveY()) if (y > maxYVal) maxYVal = y;
        }
        if (maxYVal == 0) maxYVal = 1;

        double xMin = edges[0];
        double xMax = edges[numBins];
        double xRange = xMax - xMin;

        // Масштаб осей
        double scaleX = chartW / xRange;
        double scaleY = chartH / maxYVal;

        // Столбцы гистограммы
        g.setColor(new java.awt.Color(70, 130, 180, 180));
        for (int i = 0; i < numBins; i++) {
            int x  = margin + (int) ((edges[i] - xMin) * scaleX);
            int w  = Math.max(1, (int) ((edges[i + 1] - edges[i]) * scaleX) - 1);
            int h  = (int) (freq[i] * scaleY);
            int y  = margin + chartH - h;
            g.fillRect(x, y, w, h);
            g.setColor(new java.awt.Color(50, 100, 150));
            g.drawRect(x, y, w, h);
            g.setColor(new java.awt.Color(70, 130, 180, 180));
        }

        // Кривая нормального распределения
        if (result.isShowNormalCurve() && result.getNormalCurveX().length > 1) {
            g.setColor(java.awt.Color.RED);
            g.setStroke(new java.awt.BasicStroke(2.0f));
            double[] cx = result.getNormalCurveX();
            double[] cy = result.getNormalCurveY();
            for (int i = 1; i < cx.length; i++) {
                int x1 = margin + (int) ((cx[i - 1] - xMin) * scaleX);
                int y1 = margin + chartH - (int) (cy[i - 1] * scaleY);
                int x2 = margin + (int) ((cx[i] - xMin) * scaleX);
                int y2 = margin + chartH - (int) (cy[i] * scaleY);
                g.drawLine(x1, Math.max(margin, y1), x2, Math.max(margin, y2));
            }
        }

        // Оси
        g.setColor(java.awt.Color.BLACK);
        g.setStroke(new java.awt.BasicStroke(1.5f));
        g.drawLine(margin, margin, margin, margin + chartH);
        g.drawLine(margin, margin + chartH, margin + chartW, margin + chartH);

        // Подписи осей
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        // Y-ось (несколько меток)
        int yTicks = 5;
        for (int t = 0; t <= yTicks; t++) {
            int yPx = margin + chartH - (int) (chartH * t / (double) yTicks);
            int val = (int) (maxYVal * t / yTicks);
            g.drawLine(margin - 4, yPx, margin, yPx);
            g.drawString(String.valueOf(val), margin - 42, yPx + 4);
        }
        // X-ось (метки на границах бинов)
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 9));
        int step = Math.max(1, numBins / 8);
        for (int i = 0; i <= numBins; i += step) {
            int xPx = margin + (int) ((edges[Math.min(i, numBins)] - xMin) * scaleX);
            g.drawLine(xPx, margin + chartH, xPx, margin + chartH + 4);
            String lbl = String.format("%.2f", edges[Math.min(i, numBins)]);
            g.drawString(lbl, xPx - 12, margin + chartH + 16);
        }

        // Заголовок
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14));
        g.setColor(java.awt.Color.DARK_GRAY);
        String title = "Гистограмма: «" + result.getColumnName() + "»";
        g.drawString(title, margin, margin - 10);

        g.dispose();
        return img;
    }

    /** Генерация SVG-файла с гистограммой. */
    private String buildSvg(StatResult result, int width, int height) {
        int[] freq    = result.getFrequencies();
        double[] edges = result.getBinEdges();
        int numBins   = freq.length;
        int margin    = 55;
        int chartW    = width - 2 * margin;
        int chartH    = height - 2 * margin;

        int maxFreq = 1;
        for (int f : freq) if (f > maxFreq) maxFreq = f;
        double maxYVal = maxFreq;
        if (result.isShowNormalCurve() && result.getNormalCurveY().length > 0)
            for (double y : result.getNormalCurveY()) if (y > maxYVal) maxYVal = y;

        double xMin = edges[0], xMax = edges[numBins], xRange = xMax - xMin;
        double scaleX = chartW / xRange;
        double scaleY = chartH / maxYVal;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
          .append("\" height=\"").append(height).append("\">\n");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");

        // Столбцы
        for (int i = 0; i < numBins; i++) {
            int x = margin + (int) ((edges[i] - xMin) * scaleX);
            int w = Math.max(1, (int) ((edges[i + 1] - edges[i]) * scaleX) - 1);
            int h = (int) (freq[i] * scaleY);
            int y = margin + chartH - h;
            sb.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"steelblue\" opacity=\"0.7\" stroke=\"#2a5f8a\" stroke-width=\"0.5\"/>\n",
                x, y, w, h));
        }

        // Кривая нормального распределения
        if (result.isShowNormalCurve() && result.getNormalCurveX().length > 1) {
            double[] cx = result.getNormalCurveX();
            double[] cy = result.getNormalCurveY();
            StringBuilder path = new StringBuilder("<polyline fill=\"none\" stroke=\"red\" stroke-width=\"2\" points=\"");
            for (int i = 0; i < cx.length; i++) {
                int px = margin + (int) ((cx[i] - xMin) * scaleX);
                int py = margin + chartH - (int) (cy[i] * scaleY);
                path.append(px).append(",").append(py).append(" ");
            }
            path.append("\"/>\n");
            sb.append(path);
        }

        // Оси
        sb.append(String.format("<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"black\" stroke-width=\"1.5\"/>\n",
            margin, margin, margin, margin + chartH));
        sb.append(String.format("<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"black\" stroke-width=\"1.5\"/>\n",
            margin, margin + chartH, margin + chartW, margin + chartH));

        // Заголовок
        sb.append(String.format("<text x=\"%d\" y=\"%d\" font-size=\"14\" font-weight=\"bold\" fill=\"#333\">%s</text>\n",
            margin, margin - 10, "Гистограмма: «" + result.getColumnName() + "»"));

        sb.append("</svg>");
        return sb.toString();
    }

    /** HTML с Chart.js для интерактивной гистограммы. */
    private String buildChartHtml(StatResult result) {
        int[] freq    = result.getFrequencies();
        double[] edges = result.getBinEdges();

        StringBuilder labels = new StringBuilder("[");
        StringBuilder data   = new StringBuilder("[");
        for (int i = 0; i < freq.length; i++) {
            labels.append(String.format("\"[%.2f, %.2f)\"", edges[i], edges[i + 1]));
            data.append(freq[i]);
            if (i < freq.length - 1) { labels.append(","); data.append(","); }
        }
        labels.append("]"); data.append("]");

        StringBuilder normalPoints = new StringBuilder("[");
        if (result.isShowNormalCurve() && result.getNormalCurveX().length > 0) {
            double[] cx = result.getNormalCurveX();
            double[] cy = result.getNormalCurveY();
            double xMin = edges[0], xRange = edges[freq.length] - xMin;
            double bw   = result.getBinWidth();
            for (int i = 0; i < cx.length; i++) {
                double relX = (cx[i] - xMin) / (bw); // позиция в "бинах"
                normalPoints.append(String.format("{x:%.4f,y:%.4f}", relX, cy[i]));
                if (i < cx.length - 1) normalPoints.append(",");
            }
        }
        normalPoints.append("]");

        return "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\">\n"
            + "<title>Гистограмма: " + result.getColumnName() + "</title>\n"
            + "<script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n"
            + "</head><body style=\"font-family:sans-serif;padding:20px\">\n"
            + "<h2>Гистограмма: «" + result.getColumnName() + "»</h2>\n"
            + "<canvas id=\"chart\" width=\"900\" height=\"500\"></canvas>\n"
            + "<script>\n"
            + "const ctx = document.getElementById('chart').getContext('2d');\n"
            + "new Chart(ctx, {\n"
            + "  type:'bar',\n"
            + "  data:{\n"
            + "    labels:" + labels + ",\n"
            + "    datasets:[{\n"
            + "      label:'Частота',\n"
            + "      data:" + data + ",\n"
            + "      backgroundColor:'rgba(70,130,180,0.6)',\n"
            + "      borderColor:'rgba(50,100,150,1)',\n"
            + "      borderWidth:1\n"
            + "    }]\n"
            + "  },\n"
            + "  options:{responsive:false,plugins:{title:{display:true,text:'n="
            + result.getN() + ", μ=" + String.format("%.4f", result.getMean())
            + ", σ=" + String.format("%.4f", result.getStdDev()) + "'}},\n"
            + "  scales:{x:{title:{display:true,text:'Значение'}},y:{title:{display:true,text:'Частота'}}}}\n"
            + "});\n"
            + "</script>\n"
            + "<table border=\"1\" cellpadding=\"5\" style=\"margin-top:20px;border-collapse:collapse\">\n"
            + "<tr><th>Показатель</th><th>Значение</th></tr>\n"
            + result.getStatisticsMap().entrySet().stream()
                .map(e -> "<tr><td>" + e.getKey() + "</td><td>" + e.getValue() + "</td></tr>")
                .reduce("", String::concat)
            + "</table>\n</body></html>";
    }

    private String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
