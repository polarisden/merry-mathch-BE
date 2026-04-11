package com.fsd10.merry_match_backend.entity;

/** Maps to the Postgres enum type `report_status`. */
public enum ReportStatus {
  NEW("new"),
  PENDING("pending"),
  RESOLVED("resolved"),
  CANCEL("cancel");

  private final String dbValue;

  ReportStatus(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static ReportStatus fromDbValue(String s) {
    if (s == null) return NEW;
    for (ReportStatus v : values()) {
      if (v.dbValue.equalsIgnoreCase(s)) return v;
    }
    return NEW;
  }
}
