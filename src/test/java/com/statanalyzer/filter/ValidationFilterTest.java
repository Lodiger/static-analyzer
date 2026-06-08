package com.statanalyzer.filter;

import com.statanalyzer.model.StatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationFilterTest {

    private ValidationFilter filter;
    private StatResult input;

    @BeforeEach
    void setUp() {
        filter = new ValidationFilter();
        input  = new StatResult();
        input.setColumnName("test");
    }

    @Test
    void nullData_marksInvalid() throws FilterException {
        input.setRawData(null);
        StatResult result = filter.execute(input);
        assertFalse(result.isValid());
        assertFalse(result.getErrorMessage().isEmpty());
    }

    @Test
    void emptyData_marksInvalid() throws FilterException {
        input.setRawData(new ArrayList<>());
        StatResult result = filter.execute(input);
        assertFalse(result.isValid());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void lessThanThreeValues_marksInvalid(int size) throws FilterException {
        List<String> data = new ArrayList<>();
        for (int i = 0; i < size; i++) data.add("1.0");
        input.setRawData(data);
        StatResult result = filter.execute(input);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("3"));
    }

    @Test
    void exactlyThreeValues_isValid() throws FilterException {
        input.setRawData(List.of("1.0", "2.0", "3.0"));
        StatResult result = filter.execute(input);
        assertTrue(result.isValid());
    }

    @Test
    void largeDataset_isValid() throws FilterException {
        List<String> data = new ArrayList<>();
        for (int i = 0; i < 1000; i++) data.add(String.valueOf(i * 0.5));
        input.setRawData(data);
        StatResult result = filter.execute(input);
        assertTrue(result.isValid());
    }

    @Test
    void logsArePopulated() throws FilterException {
        input.setRawData(List.of("1", "2", "3"));
        StatResult result = filter.execute(input);
        assertFalse(result.getPipelineLog().isEmpty());
    }
}
