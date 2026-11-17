package com.campus.smartorder.delivery.service;

import com.campus.smartorder.auth.service.AuthService;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.delivery.api.DeliveryDto;
import com.campus.smartorder.delivery.domain.Rider;
import com.campus.smartorder.delivery.mapper.DeliveryMapper;
import com.campus.smartorder.delivery.mapper.RiderMapper;
import com.campus.smartorder.merchant.service.OpenStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RiderService {
    private final AuthService auth;
    private final RiderMapper riders;
    private final DeliveryMapper deliveries;
    private final OpenStateService clock;

    public RiderService(AuthService auth, RiderMapper riders, DeliveryMapper deliveries, OpenStateService clock) {
        this.auth = auth;
        this.riders = riders;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> login(String studentNo, String password) {
        Map<String, Object> session = new LinkedHashMap<>(auth.loginWithPassword(studentNo, password));
        Map<?, ?> user = (Map<?, ?>) session.get("user");
        Long userId = Ids.parse(String.valueOf(user.get("userId")));
        session.put("rider", profile(userId));
        return session;
    }

    public Rider require(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        auth.requireUser(userId);
        return approved(riders.findByUser(userId));
    }

    public Rider lock(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        auth.requireUser(userId);
        return approved(riders.lockByUser(userId));
    }

    public DeliveryDto.Profile profile(Long userId) {
        return view(require(userId));
    }

    @Transactional
    public DeliveryDto.Profile changeStatus(Long userId, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只能切换接单或休息状态");
        }
        Rider rider = lock(userId);
        riders.changeStatus(rider.getId(), status);
        rider.setStatus(status);
        return view(rider);
    }

    private Rider approved(Rider rider) {
        if (rider == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "尚未开通配送员资格，请联系平台管理员");
        }
        if (rider.getStatus() == null || (rider.getStatus() != 1 && rider.getStatus() != 2)) {
            throw new BizException(ErrorCode.FORBIDDEN, rider.getStatus() != null && rider.getStatus() == 0
                    ? "配送员资格仍在审核中" : "配送员资格已停用，请联系平台管理员");
        }
        return rider;
    }

    private DeliveryDto.Profile view(Rider rider) {
        var start = clock.now().toLocalDate().atStartOfDay();
        return new DeliveryDto.Profile(Ids.of(rider.getId()), Ids.blank(rider.getRealName()),
                Ids.blank(rider.getPhone()), Ids.blank(rider.getCampusArea()), Ids.blank(rider.getDormBuilding()),
                rider.getStatus(), rider.getDeliveryCount(), rider.getIncomeAmount(), rider.getRatingScore(),
                deliveries.todayCount(rider.getId(), start, start.plusDays(1)),
                deliveries.todayIncome(rider.getId(), start, start.plusDays(1)));
    }
}
