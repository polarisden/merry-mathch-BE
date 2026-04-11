package com.fsd10.merry_match_backend.entity;

import org.hibernate.boot.model.relational.Database;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;

/**
 * Binds like {@link PostgreSQLEnumJdbcType} ({@code Types.OTHER}) for existing Postgres enums.
 * The stock type's {@code addAuxiliaryDatabaseObjects} NPEs when combined with
 * {@link jakarta.persistence.Convert} to {@link String} (Hibernate cannot enumerate JDBC-side
 * values). The {@code report_status} type already exists, so auxiliary DDL is skipped.
 */
public class ReportStatusPostgreSqlJdbcType extends PostgreSQLEnumJdbcType {

  @Override
  public void addAuxiliaryDatabaseObjects(
      JavaType<?> javaType,
      BasicValueConverter<?, ?> valueConverter,
      Size columnSize,
      Database database,
      JdbcTypeIndicators context) {
    // intentionally empty
  }
}
