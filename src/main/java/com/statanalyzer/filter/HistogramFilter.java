package com.statanalyzer.filter;

import com.statanalyzer.model.StatResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Фильтр 5: Вычисление точек кривой нормального распределения.
 * Данные для отрисовки гистограммы уже рассчитаны в StatCalcFilter;
 * здесь добавляются точки кривой нормального распределения (200 точек).
 *
 * PDF нормального распределения:
 *   f(x) = 1/(σ√(2π)) · exp(-(x-μ)² / (2σ²))
 * Масштаб для наложения на гистограмму частот:
 *   y_scaled = f(x) · n · binWidth
 */
public class HistogramFilter implements Filter<StatResult, StatResult> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int CURVE_POINTS = 200;

    @Override
    public StatResult execute(StatResult input) throws FilterException {
        log(input, "Расчёт точек кривой нормального распределения (" + CURVE_POINTS + " точек)");

        if (!input.isShowNormalCurve() || input.getStdDev() <= 0) {
            input.setNormalCurveX(new double[0]);
            input.setNormalCurveY(new double[0]);
            log(input, "Кривая нормального распределения отключена или σ=0.");
            return input;
        }

        double mu     = input.getMean();
        double sigma  = input.getStdDev();
        double n      = input.getN();
        double bw     = input.getBinWidth();

        // Диапазон: μ ± 4σ, но не выходит за min/max данных ± 10%
        double xStart = Math.min(input.getMin(), mu - 4 * sigma);
        double xEnd   = Math.max(input.getMax(), mu + 4 * sigma);
        double step   = (xEnd - xStart) / (CURVE_POINTS - 1);

        double[] curveX = new double[CURVE_POINTS];
        double[] curveY = new double[CURVE_POINTS];

        double coeff = 1.0 / (sigma * Math.sqrt(2 * Math.PI));
        for (int i = 0; i < CURVE_POINTS; i++) {
            double x = xStart + i * step;
            double exponent = -0.5 * Math.pow((x - mu) / sigma, 2);
            curveX[i] = x;
            curveY[i] = coeff * Math.exp(exponent) * n * bw;
        }

        input.setNormalCurveX(curveX);
        input.setNormalCurveY(curveY);

        double peakY = coeff * n * bw;
        log(input, String.format("Кривая: xRange=[%.3f, %.3f], пик=%.3f", xStart, xEnd, peakY));
        return input;
    }

    @Override
    public String getName() { return "HistogramFilter"; }

    private void log(StatResult result, String message) {
        result.addLog("[" + LocalTime.now().format(TIME_FMT) + "] [HistogramFilter] " + message);
    }
}
