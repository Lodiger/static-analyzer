package com.statanalyzer.filter;

import com.statanalyzer.model.StatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParseFilterTest {

    private ParseFilter filter;
    private StatResult  input;

    @BeforeEach
    void setUp() {
        filter = new ParseFilter();
        input  = new StatResult();
        input.setColumnName("col");
        input.setValid(true);
    }

    @Test
    void validNumbers_parsedCorrectly() throws FilterException {
        input.setRawData(List.of("1.5", "2.0", "-3.14", "0"));
        StatResult result = filter.execute(input);
        assertTrue(result.isValid());
        assertEquals(4, result.getParsedValues().size());
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    void commaSeparatedDecimals_parsedCorrectly() throws FilterException {
        input.setRawData(List.of("1,5", "2,7", "3,0"));
        StatResult result = filter.execute(input);
        assertEquals(3, result.getParsedValues().size());
        assertEquals(1.5, result.getParsedValues().get(0), 1e-9);
    }

    @Test
    void invalidEntries_areSkipped() throws FilterException {
        input.setRawData(List.of("1.0", "abc", "", "  ", "2.0", "NaN", "3.0"));
        StatResult result = filter.execute(input);
        assertEquals(3, result.getParsedValues().size());
        assertEquals(4, result.getSkippedCount());
    }

    @Test
    void allInvalid_marksInvalid() throws FilterException {
        input.setRawData(List.of("abc", "xyz", "???"));
        StatResult result = filter.execute(input);
        assertFalse(result.isValid());
    }

    @Test
    void negativeValues_parsedCorrectly() throws FilterException {
        input.setRawData(List.of("-1.0", "-2.5", "0.0", "3.7"));
        StatResult result = filter.execute(input);
        assertEquals(4, result.getParsedValues().size());
        assertTrue(result.getParsedValues().contains(-1.0));
    }

    @Test
    void whitespaceAroundNumbers_parsed() throws FilterException {
        input.setRawData(List.of("  5.0  ", " -3.14 ", " 0 "));
        StatResult result = filter.execute(input);
        assertEquals(3, result.getParsedValues().size());
    }
}
