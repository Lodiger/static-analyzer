package com.statanalyzer.filter;

import com.statanalyzer.model.StatResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Фильтр 4: Расчёт статистик и частот интервалов.
 *
 * Формулы:
 *   Асимметрия (Skewness Fisher):  g1 = [n/((n-1)(n-2))] * Σ[(xi-μ)³/s³]
 *   Эксцесс (Kurtosis Fisher):     g2 = [n(n+1)/((n-1)(n-2)(n-3))] * Σ[(xi-μ)⁴/s⁴]
 *                                       - 3(n-1)²/((n-2)(n-3))
 *   Коэффициент вариации:          CV = (σ/μ) * 100%
 *   Квантили (интерполяция):       Q1 = percentile(25), Q3 = percentile(75)
 */
public class StatCalcFilter implements Filter<StatResult, StatResult> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public StatResult execute(StatResult input) throws FilterException {
        log(input, "Расчёт статистических показателей...");

        List<Double> data = input.getSortedValues();
        int n = data.size();

        // ── Базовые ──────────────────────────────────────────────────────────
        double min = data.get(0);
        double max = data.get(n - 1);
        double sum = data.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / n;

        // ── Дисперсия и σ ────────────────────────────────────────────────────
        double sumSq = 0;
        for (double v : data) sumSq += (v - mean) * (v - mean);
        double variance = (n > 1) ? sumSq / (n - 1) : 0;
        double stdDev   = Math.sqrt(variance);

        // ── Медиана ──────────────────────────────────────────────────────────
        double median;
        if (n % 2 == 0) {
            median = (data.get(n / 2 - 1) + data.get(n / 2)) / 2.0;
        } else {
            median = data.get(n / 2);
        }

        // ── Квартили Q1, Q3 ─────────────────────────────────────────────────
        double q1 = percentile(data, 25);
        double q3 = percentile(data, 75);
        double iqr = q3 - q1;

        // ── Мода ─────────────────────────────────────────────────────────────
        double mode = calcMode(data);

        // ── Асимметрия (Fisher g1) ────────────────────────────────────────────
        double skewness = 0;
        if (n >= 3 && stdDev > 0) {
            double s3 = stdDev * stdDev * stdDev;
            double sumCubed = 0;
            for (double v : data) sumCubed += Math.pow(v - mean, 3);
            skewness = ((double) n / ((n - 1.0) * (n - 2.0))) * (sumCubed / s3);
        }

        // ── Эксцесс (Fisher g2, excess) ───────────────────────────────────────
        double kurtosis = 0;
        if (n >= 4 && stdDev > 0) {
            double s4 = Math.pow(stdDev, 4);
            double sumFourth = 0;
            for (double v : data) sumFourth += Math.pow(v - mean, 4);
            double term1 = ((double) n * (n + 1)) / ((n - 1.0) * (n - 2.0) * (n - 3.0)) * (sumFourth / s4);
            double term2 = (3.0 * (n - 1.0) * (n - 1.0)) / ((n - 2.0) * (n - 3.0));
            kurtosis = term1 - term2;
        }

        // ── Коэффициент вариации ──────────────────────────────────────────────
        double cv = (mean != 0) ? (stdDev / Math.abs(mean)) * 100.0 : 0;

        // ── Частоты интервалов гистограммы ────────────────────────────────────
        int numBins = input.getNumBins();
        double range  = max - min;
        double binWidth = (range == 0) ? 1.0 : range / numBins;

        double[] binEdges = new double[numBins + 1];
        for (int i = 0; i <= numBins; i++) {
            binEdges[i] = min + i * binWidth;
        }
        binEdges[numBins] = max; // гарантируем включение максимума

        int[] frequencies = new int[numBins];
        for (double v : data) {
            int bin = (int) ((v - min) / binWidth);
            if (bin >= numBins) bin = numBins - 1;
            frequencies[bin]++;
        }

        double[] relFreq = new double[numBins];
        for (int i = 0; i < numBins; i++) {
            relFreq[i] = (double) frequencies[i] / n;
        }

        // ── Запись в результат ────────────────────────────────────────────────
        input.setN(n);
        input.setMin(min);
        input.setMax(max);
        input.setSum(sum);
        input.setSumSquares(sumSq);
        input.setMean(mean);
        input.setMedian(median);
        input.setMode(mode);
        input.setVariance(variance);
        input.setStdDev(stdDev);
        input.setQ1(q1);
        input.setQ3(q3);
        input.setIqr(iqr);
        input.setRange(range);
        input.setSkewness(skewness);
        input.setKurtosis(kurtosis);
        input.setCoefficientOfVariation(cv);
        input.setBinWidth(binWidth);
        input.setBinEdges(binEdges);
        input.setFrequencies(frequencies);
        input.setRelativeFrequencies(relFreq);

        log(input, String.format("Расчёт завершён: n=%d, μ=%.4f, σ=%.4f, Asym=%.4f, Kurt=%.4f",
            n, mean, stdDev, skewness, kurtosis));
        return input;
    }

    /** Линейная интерполяция процентиля для отсортированного списка. */
    private double percentile(List<Double> sorted, double p) {
        int n = sorted.size();
        double index = p / 100.0 * (n - 1);
        int lo = (int) index;
        int hi = lo + 1;
        if (hi >= n) return sorted.get(n - 1);
        double frac = index - lo;
        return sorted.get(lo) * (1 - frac) + sorted.get(hi) * frac;
    }

    /** Возвращает значение с наибольшей частотой (первое при равенстве). */
    private double calcMode(List<Double> data) {
        Map<Double, Integer> freq = new HashMap<>();
        for (double v : data) freq.merge(v, 1, Integer::sum);
        return freq.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(data.get(0));
    }

    @Override
    public String getName() { return "StatCalcFilter"; }

    private void log(StatResult result, String message) {
        result.addLog("[" + LocalTime.now().format(TIME_FMT) + "] [StatCalcFilter] " + message);
    }
}
