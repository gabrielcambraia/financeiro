package com.financeiro.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

/**
 * O driver JDBC do SQLite (xerial) grava LocalDate/LocalDateTime nativos como
 * epoch millis, mas as colunas TEXT existentes (criadas via Flyway) esperam
 * uma string ISO — sem este conversor explícito, a leitura de volta falha
 * com "Error parsing time stamp". Mantém o formato yyyy-MM-dd usado desde as
 * migrations originais.
 */
@Converter(autoApply = false)
public class ConversorLocalDate implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LocalDate.parse(dbData);
    }
}
