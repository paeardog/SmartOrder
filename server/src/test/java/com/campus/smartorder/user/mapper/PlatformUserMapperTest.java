package com.campus.smartorder.user.mapper;

import com.campus.smartorder.user.api.PlatformUserDto;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlatformUserMapperTest {

    private Configuration configuration;

    @BeforeEach
    void setUp() {
        configuration = new Configuration();
        new MapperAnnotationBuilder(configuration, UserMapper.class).parse();
    }

    @Test
    void statusUpdateBindsExpectedStatusAndReasonWithoutOverwritingOtherFields() {
        String reason = "' WHERE 1=1 --";
        BoundSql statement = configuration.getMappedStatement(UserMapper.class.getName() + ".compareAndSetStatus")
                .getBoundSql(Map.of("userId", 7L, "expectedStatus", 1, "status", 0, "reason", reason));
        String sql = statement.getSql().replaceAll("\\s+", " ").trim();
        assertEquals("UPDATE u_user SET status = ?, forbidden_reason = ?, updated_at = NOW(3) "
                + "WHERE id = ? AND status = ?", sql);
        assertEquals(List.of("status", "reason", "userId", "expectedStatus"),
                statement.getParameterMappings().stream().map(ParameterMapping::getProperty).toList());
        assertFalse(sql.contains(reason));
        assertFalse(sql.contains("password_hash"));
    }

    @Test
    void summaryCountsAllUsersAndPendingVerificationInOneQuery() {
        var statement = configuration.getMappedStatement(UserMapper.class.getName() + ".selectPlatformUserSummary");
        String sql = statement.getBoundSql(null).getSql().replaceAll("\\s+", " ").trim();
        assertEquals("SELECT COUNT(*) AS total, COUNT(CASE WHEN status = 1 THEN 1 END) AS normal, "
                + "COUNT(CASE WHEN status = 0 THEN 1 END) AS banned, "
                + "COUNT(CASE WHEN verify_status = 1 THEN 1 END) AS pendingVerification FROM u_user", sql);
        assertEquals(PlatformUserDto.Summary.class, statement.getResultMaps().get(0).getType());
    }
}
