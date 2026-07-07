package com.financeiro.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ver {@link ConversorLocalDate} — mesmo problema de serialização do driver
 * JDBC do SQLite, aqui para colunas "criado_em" (formato ISO_LOCAL_DATE_TIME,
 * o mesmo já gravado pelas migrations existentes).
 */
@Converter(autoApply = false)
public class ConversorLocalDateTime implements AttributeConverter<LocalDateTime, String> {

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : attribute.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LocalDateTime.parse(dbData);
    }
}
