package com.campus.smartorder.system.api;

import com.campus.smartorder.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final LocalDateTime startedAt = LocalDateTime.now();

    private final JdbcTemplate jdbcTemplate;

    public SystemController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", "smartorder-server");
        body.put("dbNow", jdbcTemplate.queryForObject("SELECT NOW(3)", LocalDateTime.class));
        body.put("dbTimezone", jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class));
        body.put("isolation", jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class));
        body.put("tableCount", count("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                """));
        body.put("migratedVersion", jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 'none') FROM flyway_schema_history WHERE success = 1", String.class));
        return ApiResponse.ok(body);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchants", count("SELECT COUNT(*) FROM m_merchant WHERE deleted_at IS NULL"));
        body.put("dishes", count("SELECT COUNT(*) FROM m_dish WHERE deleted_flag = 0"));
        body.put("orders", count("SELECT COUNT(*) FROM o_order"));
        body.put("pendingPayment", count("SELECT COUNT(*) FROM o_order WHERE status = 'PENDING_PAYMENT'"));
        body.put("readyOrders", count("SELECT COUNT(*) FROM o_order WHERE status = 'READY'"));
        body.put("uptimeSeconds", Duration.between(startedAt, LocalDateTime.now()).getSeconds());
        return ApiResponse.ok(body);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
