package com.campus.smartorder.merchant.mapper;

import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MerchantMapperTest {

    @Test
    void statusChangeOnlyUpdatesMerchantStatusAndChecksCurrentStateAndDeletion() throws Exception {
        BoundSql sql = sql(null);
        assertEquals("UPDATE m_merchant SET status = ? WHERE id = ? AND status = ? AND deleted_at IS NULL",
                sql.getSql().replaceAll("\\s+", " ").trim());
        assertEquals(List.of("status", "merchantId", "expectedStatus"), parameters(sql));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "资料不全"})
    void auditUpdatesReasonAtomicallyWithStatus(String reason) throws Exception {
        BoundSql sql = sql(reason);
        assertEquals("UPDATE m_merchant SET status = ? , reject_reason = ? WHERE id = ? AND status = ? AND deleted_at IS NULL",
                sql.getSql().replaceAll("\\s+", " ").trim());
        assertEquals(List.of("status", "rejectReason", "merchantId", "expectedStatus"), parameters(sql));
    }

    private BoundSql sql(String reason) throws Exception {
        String script = String.join("\n", MerchantMapper.class
                .getMethod("casStatus", Long.class, Integer.class, Integer.class, String.class)
                .getAnnotation(Update.class).value());
        Map<String, Object> params = new HashMap<>();
        params.put("merchantId", 10L);
        params.put("expectedStatus", 0);
        params.put("status", 1);
        params.put("rejectReason", reason);
        return new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class).getBoundSql(params);
    }

    private List<String> parameters(BoundSql sql) {
        return sql.getParameterMappings().stream().map(ParameterMapping::getProperty).toList();
    }
}
