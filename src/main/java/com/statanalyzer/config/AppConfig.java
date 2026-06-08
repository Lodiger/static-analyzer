package com.statanalyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;

/**
 * Конфигурация приложения (сериализуется в JSON).
 * Хранит параметры анализа и настройки отображения.
 */
public class AppConfig {

    // ── Параметры гистограммы ─────────────────────────────────────────────
    private int    numBins       = 10;
    private double iqrMultiplier = 1.5;

    // ── Флаги отображения ─────────────────────────────────────────────────
    private boolean showNormalCurve = true;
    private boolean showOutliers    = false;
    private boolean showGrid        = true;
    private boolean showValues      = false;

    // ── Путь последней директории ─────────────────────────────────────────
    private String lastDirectory = System.getProperty("user.home");

    // ── Цветовая схема (название) ─────────────────────────────────────────
    private String colorScheme = "steelblue";

    // ─────────────────────────────────────────────────────────────────────────
    // Сериализация
    // ─────────────────────────────────────────────────────────────────────────

    private static final ObjectMapper MAPPER =
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void save(File file) throws IOException {
        MAPPER.writeValue(file, this);
    }

    public static AppConfig load(File file) throws IOException {
        return MAPPER.readValue(file, AppConfig.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters / Setters
    // ─────────────────────────────────────────────────────────────────────────

    public int getNumBins() { return numBins; }
    public void setNumBins(int numBins) { this.numBins = Math.max(2, Math.min(100, numBins)); }

    public double getIqrMultiplier() { return iqrMultiplier; }
    public void setIqrMultiplier(double iqrMultiplier) { this.iqrMultiplier = iqrMultiplier; }

    public boolean isShowNormalCurve() { return showNormalCurve; }
    public void setShowNormalCurve(boolean showNormalCurve) { this.showNormalCurve = showNormalCurve; }

    public boolean isShowOutliers() { return showOutliers; }
    public void setShowOutliers(boolean showOutliers) { this.showOutliers = showOutliers; }

    public boolean isShowGrid() { return showGrid; }
    public void setShowGrid(boolean showGrid) { this.showGrid = showGrid; }

    public boolean isShowValues() { return showValues; }
    public void setShowValues(boolean showValues) { this.showValues = showValues; }

    public String getLastDirectory() { return lastDirectory; }
    public void setLastDirectory(String lastDirectory) { this.lastDirectory = lastDirectory; }

    public String getColorScheme() { return colorScheme; }
    public void setColorScheme(String colorScheme) { this.colorScheme = colorScheme; }
}
