module com.statanalyzer {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.desktop;
    requires java.logging;

    // Именованные автоматические модули и JPMS-модули сторонних библиотек
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires org.apache.pdfbox;
    requires com.opencsv;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;

    // Оставшиеся transitive-зависимости без Automatic-Module-Name — через unnamed

    // FXML-рефлексия контроллеров
    opens com.statanalyzer to javafx.fxml;
    opens com.statanalyzer.controller to javafx.fxml;
    // Jackson-рефлексия (десериализация)
    opens com.statanalyzer.model;
    opens com.statanalyzer.config;

    exports com.statanalyzer;
    exports com.statanalyzer.controller;
    exports com.statanalyzer.filter;
    exports com.statanalyzer.model;
    exports com.statanalyzer.pipeline;
    exports com.statanalyzer.config;
}
