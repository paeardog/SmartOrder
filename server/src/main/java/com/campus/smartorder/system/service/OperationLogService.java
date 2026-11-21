package com.campus.smartorder.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.console.ConsoleAccess;
import com.campus.smartorder.system.domain.OperationLog;
import com.campus.smartorder.system.mapper.OperationLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

@Service
public class OperationLogService {
    private final OperationLogMapper mapper;
    private final ObjectMapper objectMapper;

    public OperationLogService(OperationLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public record Row(String logId, String accountId, String action, String targetType,
                      String targetId, String clientIp, LocalDateTime createdAt) {}
    public record Result(List<Row> list, long total, int page, int pageSize) {}

    public Result list(int page, int pageSize, String keyword, Long accountId) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || (accountId != null && accountId <= 0)
                || (keyword != null && keyword.length() > 64)) {
            throw new BizException(ErrorCode.INVALID_PARAM);
        }
        String text = keyword == null ? "" : keyword.trim();
        var query = new LambdaQueryWrapper<OperationLog>()
                .eq(accountId != null, OperationLog::getAccountId, accountId)
                .and(!text.isEmpty(), q -> q.like(OperationLog::getAction, text)
                        .or().like(OperationLog::getTargetType, text).or().like(OperationLog::getTargetId, text))
                .orderByDesc(OperationLog::getId);
        Page<OperationLog> result = mapper.selectPage(new Page<>(page, pageSize), query);
        return new Result(result.getRecords().stream().map(log -> new Row(Ids.of(log.getId()),
                log.getAccountId() == null ? "" : Ids.of(log.getAccountId()), log.getAction(),
                log.getTargetType(), log.getTargetId(), Ids.blank(log.getClientIp()), log.getCreatedAt())).toList(),
                result.getTotal(), page, pageSize);
    }

    @Transactional
    public <T> T execute(HttpServletRequest request, String action, String targetType, String targetId,
                         Supplier<T> operation) {
        Long accountId = ConsoleAccess.accountId(request);
        T result = operation.get();
        OperationLog log = new OperationLog();
        log.setAccountId(accountId);
        log.setAction(action);
        log.setTargetType(targetType);
        String id = targetId;
        if (id == null && result != null) {
            JsonNode node = objectMapper.valueToTree(result);
            for (String field : List.of("accountId", "userId", "merchantId", "refundNo")) {
                if (node.hasNonNull(field)) {
                    id = node.get(field).asText();
                    break;
                }
            }
        }
        log.setTargetId(id == null ? "" : id);
        log.setClientIp(request.getRemoteAddr());
        log.setCreatedAt(LocalDateTime.now());
        mapper.insert(log);
        return result;
    }
}
