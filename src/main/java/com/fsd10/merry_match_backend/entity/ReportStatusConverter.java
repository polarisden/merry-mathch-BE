package com.fsd10.merry_match_backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Converts between ReportStatus enum and Postgres `report_status` enum string. */
@Converter(autoApply = false)
public class ReportStatusConverter implements AttributeConverter<ReportStatus, String> {

  @Override
  public String convertToDatabaseColumn(ReportStatus attribute) {
    if (attribute == null) return ReportStatus.NEW.getDbValue();
    return attribute.getDbValue();
  }

  @Override
  public ReportStatus convertToEntityAttribute(String dbData) {
    return ReportStatus.fromDbValue(dbData);
  }
}
