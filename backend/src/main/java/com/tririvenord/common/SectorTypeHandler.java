package com.tririvenord.common;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reads and writes {@link Sector} as the plain {@code varchar} it has always
 * been in the database.
 *
 * <p>MyBatis handled this for free while Sector was an enum. It is written out
 * here rather than letting the record fall back to a generic object handler,
 * which would store something like {@code Sector[code=north]} and quietly
 * corrupt a column that four years of migrations fill with bare codes.
 */
@MappedTypes(Sector.class)
public class SectorTypeHandler extends BaseTypeHandler<Sector> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Sector parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.code());
    }

    @Override
    public Sector getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return from(rs.getString(columnName));
    }

    @Override
    public Sector getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return from(rs.getString(columnIndex));
    }

    @Override
    public Sector getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return from(cs.getString(columnIndex));
    }

    private static Sector from(String value) {
        return value == null ? null : Sector.of(value);
    }
}
