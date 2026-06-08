package com.statanalyzer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class StatResult {

    // ── Input ────────────────────────────────────────────────────────────────
    private String columnName = "";
    private List<String> rawData = new ArrayList<>();

    // ── После ParseFilter ────────────────────────────────────────────────────
    private List<Double> parsedValues = new ArrayList<>();
    private int skippedCount = 0;

    // ── После SortFilter ─────────────────────────────────────────────────────
    private List<Double> sortedValues = new ArrayList<>();

    // ── Статистики (StatCalcFilter) ──────────────────────────────────────────
    private int n = 0;
    private double mean = 0;
    private double median = 0;
    private double mode = 0;
    private double stdDev = 0;
    private double variance = 0;
    private double skewness = 0;
    private double kurtosis = 0;
    private double coefficientOfVariation = 0;
    private double min = 0;
    private double max = 0;
    private double range = 0;
    private double q1 = 0;
    private double q3 = 0;
    private double iqr = 0;
    private double sum = 0;
    private double sumSquares = 0;

    // ── Гистограмма (HistogramFilter) ────────────────────────────────────────
    private int numBins = 10;
    private double binWidth = 0;
    private double[] binEdges = new double[0];
    private int[] frequencies = new int[0];
    private double[] relativeFrequencies = new double[0];
    private double[] normalCurveX = new double[0];
    private double[] normalCurveY = new double[0];

    // ── Конфиг анализа ───────────────────────────────────────────────────────
    private boolean showNormalCurve = true;
    private boolean showOutliers = false;
    private double iqrMultiplier = 1.5;

    // ── Журнал конвейера ─────────────────────────────────────────────────────
    private List<String> pipelineLog = new ArrayList<>();

    // ── Статус ───────────────────────────────────────────────────────────────
    private boolean valid = true;
    private String errorMessage = "";

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    public void addLog(String message) {
        pipelineLog.add(message);
    }

    /** Возвращает все статистики в виде упорядоченной карты (название → значение). */
    public Map<String, String> getStatisticsMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Количество значений (n)", String.valueOf(n));
        map.put("Пропущено (некорректных)", String.valueOf(skippedCount));
        map.put("Минимум", fmt(min));
        map.put("Максимум", fmt(max));
        map.put("Размах", fmt(range));
        map.put("Сумма", fmt(sum));
        map.put("Среднее (μ)", fmt(mean));
        map.put("Медиана", fmt(median));
        map.put("Мода", fmt(mode));
        map.put("Стандартное отклонение (σ)", fmt(stdDev));
        map.put("Дисперсия (σ²)", fmt(variance));
        map.put("Квартиль Q1", fmt(q1));
        map.put("Квартиль Q3", fmt(q3));
        map.put("МКР (IQR)", fmt(iqr));
        map.put("Асимметрия (Skewness)", fmt(skewness));
        map.put("Эксцесс (Kurtosis)", fmt(kurtosis));
        map.put("Коэффициент вариации (%)", fmt(coefficientOfVariation));
        return map;
    }

    private String fmt(double v) {
        return String.format("%.6f", v);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters / Setters
    // ─────────────────────────────────────────────────────────────────────────

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public List<String> getRawData() { return rawData; }
    public void setRawData(List<String> rawData) { this.rawData = rawData; }

    public List<Double> getParsedValues() { return parsedValues; }
    public void setParsedValues(List<Double> parsedValues) { this.parsedValues = parsedValues; }

    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }

    public List<Double> getSortedValues() { return sortedValues; }
    public void setSortedValues(List<Double> sortedValues) { this.sortedValues = sortedValues; }

    public int getN() { return n; }
    public void setN(int n) { this.n = n; }

    public double getMean() { return mean; }
    public void setMean(double mean) { this.mean = mean; }

    public double getMedian() { return median; }
    public void setMedian(double median) { this.median = median; }

    public double getMode() { return mode; }
    public void setMode(double mode) { this.mode = mode; }

    public double getStdDev() { return stdDev; }
    public void setStdDev(double stdDev) { this.stdDev = stdDev; }

    public double getVariance() { return variance; }
    public void setVariance(double variance) { this.variance = variance; }

    public double getSkewness() { return skewness; }
    public void setSkewness(double skewness) { this.skewness = skewness; }

    public double getKurtosis() { return kurtosis; }
    public void setKurtosis(double kurtosis) { this.kurtosis = kurtosis; }

    public double getCoefficientOfVariation() { return coefficientOfVariation; }
    public void setCoefficientOfVariation(double cv) { this.coefficientOfVariation = cv; }

    public double getMin() { return min; }
    public void setMin(double min) { this.min = min; }

    public double getMax() { return max; }
    public void setMax(double max) { this.max = max; }

    public double getRange() { return range; }
    public void setRange(double range) { this.range = range; }

    public double getQ1() { return q1; }
    public void setQ1(double q1) { this.q1 = q1; }

    public double getQ3() { return q3; }
    public void setQ3(double q3) { this.q3 = q3; }

    public double getIqr() { return iqr; }
    public void setIqr(double iqr) { this.iqr = iqr; }

    public double getSum() { return sum; }
    public void setSum(double sum) { this.sum = sum; }

    public double getSumSquares() { return sumSquares; }
    public void setSumSquares(double sumSquares) { this.sumSquares = sumSquares; }

    public int getNumBins() { return numBins; }
    public void setNumBins(int numBins) { this.numBins = numBins; }

    public double getBinWidth() { return binWidth; }
    public void setBinWidth(double binWidth) { this.binWidth = binWidth; }

    public double[] getBinEdges() { return binEdges; }
    public void setBinEdges(double[] binEdges) { this.binEdges = binEdges; }

    public int[] getFrequencies() { return frequencies; }
    public void setFrequencies(int[] frequencies) { this.frequencies = frequencies; }

    public double[] getRelativeFrequencies() { return relativeFrequencies; }
    public void setRelativeFrequencies(double[] rf) { this.relativeFrequencies = rf; }

    public double[] getNormalCurveX() { return normalCurveX; }
    public void setNormalCurveX(double[] x) { this.normalCurveX = x; }

    public double[] getNormalCurveY() { return normalCurveY; }
    public void setNormalCurveY(double[] y) { this.normalCurveY = y; }

    public boolean isShowNormalCurve() { return showNormalCurve; }
    public void setShowNormalCurve(boolean showNormalCurve) { this.showNormalCurve = showNormalCurve; }

    public boolean isShowOutliers() { return showOutliers; }
    public void setShowOutliers(boolean showOutliers) { this.showOutliers = showOutliers; }

    public double getIqrMultiplier() { return iqrMultiplier; }
    public void setIqrMultiplier(double iqrMultiplier) { this.iqrMultiplier = iqrMultiplier; }

    public List<String> getPipelineLog() { return pipelineLog; }
    public void setPipelineLog(List<String> log) { this.pipelineLog = log; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
