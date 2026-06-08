package com.statanalyzer.filter;

import com.statanalyzer.model.StatResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Фильтр 3: Сортировка значений по возрастанию.
 * Результат нужен для расчёта квантилей, медианы и IQR.
 */
public class SortFilter implements Filter<StatResult, StatResult> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public StatResult execute(StatResult input) throws FilterException {
        log(input, "Сортировка " + input.getParsedValues().size() + " значений по возрастанию");

        List<Double> sorted = new ArrayList<>(input.getParsedValues());
        Collections.sort(sorted);

        input.setSortedValues(sorted);
        log(input, "Сортировка завершена. Диапазон: [" +
            String.format("%.4f", sorted.get(0)) + " … " +
            String.format("%.4f", sorted.get(sorted.size() - 1)) + "]");
        return input;
    }

    @Override
    public String getName() { return "SortFilter"; }

    private void log(StatResult result, String message) {
        result.addLog("[" + LocalTime.now().format(TIME_FMT) + "] [SortFilter] " + message);
    }
}
