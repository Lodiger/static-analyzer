package com.statanalyzer.pipeline;

import com.statanalyzer.filter.*;
import com.statanalyzer.model.StatResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты полного конвейера.
 * ValidationFilter → ParseFilter → SortFilter → StatCalcFilter → HistogramFilter
 */
class PipelineIntegrationTest {

    private Pipeline buildPipeline() {
        return new Pipeline()
            .addFilter(new ValidationFilter())
            .addFilter(new ParseFilter())
            .addFilter(new SortFilter())
            .addFilter(new StatCalcFilter())
            .addFilter(new HistogramFilter());
    }

    private StatResult inputOf(String column, List<String> rawData) {
        StatResult r = new StatResult();
        r.setColumnName(column);
        r.setRawData(rawData);
        r.setNumBins(5);
        r.setShowNormalCurve(true);
        r.setIqrMultiplier(1.5);
        return r;
    }

    @Test
    void validNumericData_fullPipelineSucceeds() {
        List<String> data = List.of("1.0","2.5","3.0","4.5","5.0","6.0","7.5","8.0","9.0","10.0");
        StatResult result = buildPipeline().execute(inputOf("price", data));

        assertTrue(result.isValid(), "Конвейер должен успешно завершиться");
        assertEquals(10, result.getN());
        assertTrue(result.getMean() > 0);
        assertTrue(result.getStdDev() > 0);
        assertEquals(5, result.getFrequencies().length);
        assertTrue(result.getNormalCurveX().length > 0);
    }

    @Test
    void emptyData_pipelineStopsAtValidation() {
        StatResult result = buildPipeline().execute(inputOf("empty", new ArrayList<>()));
        assertFalse(result.isValid());
        assertTrue(result.getPipelineLog().stream()
            .anyMatch(line -> line.contains("ValidationFilter")));
    }

    @Test
    void allInvalidStrings_pipelineStopsAtParse() {
        StatResult result = buildPipeline().execute(inputOf("bad", List.of("abc","xyz","???")));
        assertFalse(result.isValid());
    }

    @Test
    void mixedData_invalidEntriesSkipped() {
        List<String> data = List.of("1.0","abc","2.0","","3.5","null","4.0","5.0");
        StatResult result = buildPipeline().execute(inputOf("mixed", data));
        assertTrue(result.isValid());
        assertEquals(5, result.getN());
        assertEquals(3, result.getSkippedCount());
    }

    @Test
    void negativeValues_handledCorrectly() {
        List<String> data = List.of("-5.0","-3.0","-1.0","0.0","1.0","3.0","5.0");
        StatResult result = buildPipeline().execute(inputOf("neg", data));
        assertTrue(result.isValid());
        assertEquals(-5.0, result.getMin(), 1e-9);
        assertEquals(5.0,  result.getMax(), 1e-9);
        assertEquals(0.0,  result.getMean(), 1e-9);
    }

    @Test
    void pipelineLog_containsAllFilterNames() {
        List<String> data = List.of("1","2","3","4","5");
        StatResult result = buildPipeline().execute(inputOf("log_test", data));

        List<String> log = result.getPipelineLog();
        assertTrue(log.stream().anyMatch(l -> l.contains("ValidationFilter")));
        assertTrue(log.stream().anyMatch(l -> l.contains("ParseFilter")));
        assertTrue(log.stream().anyMatch(l -> l.contains("SortFilter")));
        assertTrue(log.stream().anyMatch(l -> l.contains("StatCalcFilter")));
        assertTrue(log.stream().anyMatch(l -> l.contains("HistogramFilter")));
    }

    @Test
    void sortedValues_areActuallySorted() {
        List<String> data = List.of("9","3","7","1","5","2","8","4","6","10");
        StatResult result = buildPipeline().execute(inputOf("order", data));
        assertTrue(result.isValid());
        List<Double> sorted = result.getSortedValues();
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue(sorted.get(i) >= sorted.get(i - 1),
                "Значения не отсортированы на позиции " + i);
        }
    }

    @Test
    void binFrequenciesSumToN() {
        List<String> data = new ArrayList<>();
        for (int i = 1; i <= 50; i++) data.add(String.valueOf(i));
        StatResult result = buildPipeline().execute(inputOf("sum_test", data));
        assertTrue(result.isValid());
        int total = 0;
        for (int f : result.getFrequencies()) total += f;
        assertEquals(result.getN(), total);
    }
}
