package com.fsd10.merry_match_backend.entity;

import org.hibernate.boot.model.relational.Database;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;

/**
 * Use existing PostgreSQL enum type without generating DROP/CREATE enum DDL.
 */
public class BillingStatusPostgreSqlJdbcType extends PostgreSQLEnumJdbcType {

    @Override
    public void addAuxiliaryDatabaseObjects(
            JavaType<?> javaType,
            BasicValueConverter<?, ?> valueConverter,
            Size columnSize,
            Database database,
            JdbcTypeIndicators context) {
        // no-op: schema enum lifecycle is managed by explicit SQL migrations
    }
}
