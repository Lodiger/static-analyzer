package com.statanalyzer.filter;

import com.statanalyzer.model.StatResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Фильтр 1: Валидация входных данных.
 * Проверяет, что rawData не пуст и содержит хотя бы 3 значения.
 */
public class ValidationFilter implements Filter<StatResult, StatResult> {

    private static final int MIN_VALUES = 3;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public StatResult execute(StatResult input) throws FilterException {
        log(input, "Запуск валидации данных для колонки: «" + input.getColumnName() + "»");

        if (input.getRawData() == null || input.getRawData().isEmpty()) {
            String msg = "Данные отсутствуют. Загрузите файл и выберите колонку.";
            log(input, "ОШИБКА: " + msg);
            input.setValid(false);
            input.setErrorMessage(msg);
            return input;
        }

        if (input.getRawData().size() < MIN_VALUES) {
            String msg = "Недостаточно данных: " + input.getRawData().size()
                + " значений. Минимум — " + MIN_VALUES + ".";
            log(input, "ОШИБКА: " + msg);
            input.setValid(false);
            input.setErrorMessage(msg);
            return input;
        }

        log(input, "Валидация пройдена: " + input.getRawData().size() + " строк в колонке.");
        input.setValid(true);
        return input;
    }

    @Override
    public String getName() { return "ValidationFilter"; }

    private void log(StatResult result, String message) {
        result.addLog("[" + LocalTime.now().format(TIME_FMT) + "] [ValidationFilter] " + message);
    }
}
