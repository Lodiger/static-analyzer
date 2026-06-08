package com.statanalyzer.filter;

import com.statanalyzer.model.StatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatCalcFilterTest {

    private static final double EPS = 1e-6;
    private StatCalcFilter filter;
    private StatResult     input;

    @BeforeEach
    void setUp() {
        filter = new StatCalcFilter();
        input  = new StatResult();
        input.setColumnName("col");
        input.setValid(true);
        input.setNumBins(5);
    }

    private void setSorted(double... values) {
        List<Double> sorted = Arrays.stream(values).boxed().sorted().toList();
        input.setParsedValues(sorted);
        input.setSortedValues(sorted);
    }

    @Test
    void mean_calculatedCorrectly() throws FilterException {
        setSorted(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        StatResult r = filter.execute(input);
        assertEquals(5.0, r.getMean(), EPS);
    }

    @Test
    void variance_calculatedCorrectly() throws FilterException {
        setSorted(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        StatResult r = filter.execute(input);
        assertEquals(4.0, r.getVariance(), EPS);
    }

    @Test
    void stdDev_calculatedCorrectly() throws FilterException {
        setSorted(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        StatResult r = filter.execute(input);
        assertEquals(2.0, r.getStdDev(), EPS);
    }

    @Test
    void median_odd_calculatedCorrectly() throws FilterException {
        setSorted(1.0, 3.0, 5.0, 7.0, 9.0);
        StatResult r = filter.execute(input);
        assertEquals(5.0, r.getMedian(), EPS);
    }

    @Test
    void median_even_calculatedCorrectly() throws FilterException {
        setSorted(1.0, 2.0, 3.0, 4.0);
        StatResult r = filter.execute(input);
        assertEquals(2.5, r.getMedian(), EPS);
    }

    @Test
    void minMax_correct() throws FilterException {
        setSorted(-5.0, 0.0, 3.0, 10.0, 15.0);
        StatResult r = filter.execute(input);
        assertEquals(-5.0, r.getMin(), EPS);
        assertEquals(15.0, r.getMax(), EPS);
        assertEquals(20.0, r.getRange(), EPS);
    }

    @Test
    void histogramFrequencies_sumToN() throws FilterException {
        setSorted(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);
        StatResult r = filter.execute(input);
        int sum = 0;
        for (int f : r.getFrequencies()) sum += f;
        assertEquals(r.getN(), sum);
    }

    @Test
    void coefficientOfVariation_nonzeroMean() throws FilterException {
        setSorted(10.0, 20.0, 30.0);
        StatResult r = filter.execute(input);
        assertTrue(r.getCoefficientOfVariation() > 0);
    }

    @Test
    void symmetricData_skewnessNearZero() throws FilterException {
        // Симметричное распределение — асимметрия должна быть близка к нулю
        setSorted(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
        StatResult r = filter.execute(input);
        assertEquals(0.0, r.getSkewness(), 0.1);
    }

    @Test
    void normalData_kurtosisNearZero() throws FilterException {
        // При нормальном распределении избыточный эксцесс ≈ 0
        setSorted(1.0, 2.0, 3.0, 3.0, 4.0, 4.0, 5.0, 5.0, 6.0, 7.0);
        StatResult r = filter.execute(input);
        assertTrue(Math.abs(r.getKurtosis()) < 3.0); // разумный диапазон
    }
}
