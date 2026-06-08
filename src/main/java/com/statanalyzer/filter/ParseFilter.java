package com.statanalyzer.filter;

import com.statanalyzer.model.StatResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Фильтр 2: Парсинг строк в числа.
 * Пропускает некорректные значения (пустые строки, текст, NaN), логирует количество пропущенных.
 */
public class ParseFilter implements Filter<StatResult, StatResult> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public StatResult execute(StatResult input) throws FilterException {
        log(input, "Парсинг " + input.getRawData().size() + " строк → List<Double>");

        List<Double> parsed = new ArrayList<>();
        int skipped = 0;

        for (String raw : input.getRawData()) {
            if (raw == null || raw.isBlank()) {
                skipped++;
                continue;
            }
            String cleaned = raw.trim().replace(',', '.');
            try {
                double value = Double.parseDouble(cleaned);
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    skipped++;
                } else {
                    parsed.add(value);
                }
            } catch (NumberFormatException e) {
                skipped++;
            }
        }

        if (parsed.size() < 3) {
            String msg = "После парсинга осталось менее 3 числовых значений (" + parsed.size() + ").";
            log(input, "ОШИБКА: " + msg);
            input.setValid(false);
            input.setErrorMessage(msg);
            return input;
        }

        input.setParsedValues(parsed);
        input.setSkippedCount(skipped);
        log(input, "Распознано: " + parsed.size() + " чисел. Пропущено: " + skipped + ".");
        return input;
    }

    @Override
    public String getName() { return "ParseFilter"; }

    private void log(StatResult result, String message) {
        result.addLog("[" + LocalTime.now().format(TIME_FMT) + "] [ParseFilter] " + message);
    }
}
