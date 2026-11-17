package com.campus.smartorder.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.api.PageDto;
import com.campus.smartorder.delivery.api.DeliveryDto;
import com.campus.smartorder.delivery.domain.Delivery;
import com.campus.smartorder.delivery.domain.Rider;
import com.campus.smartorder.delivery.mapper.DeliveryMapper;
import com.campus.smartorder.delivery.mapper.RiderMapper;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.order.domain.FulfillmentType;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.service.DeliveryOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

@Service
public class DeliveryService {
    private final DeliveryMapper deliveries;
    private final RiderMapper riders;
    private final RiderService riderService;
    private final DeliveryOrderService orders;
    private final OpenStateService clock;

    public DeliveryService(DeliveryMapper deliveries, RiderMapper riders, RiderService riderService,
                           DeliveryOrderService orders, OpenStateService clock) {
        this.deliveries = deliveries;
        this.riders = riders;
        this.riderService = riderService;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(Order order) {
        if (order.getFulfillmentType() != FulfillmentType.DELIVERY) {
            return;
        }
        if (order.getStatus() != OrderStatus.READY || Ids.blank(order.getDormAddress()).isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "配送订单缺少有效收货地址，不能进入配送池");
        }
        String address = order.getDormAddress();
        int roomStart = address.indexOf('\n');
        String building = roomStart > 0 ? address.substring(0, roomStart) : "";
        deliveries.enqueue(order.getId(), order.getDeliveryFee(), building.substring(0, Math.min(64, building.length())));
    }

    public PageDto<DeliveryDto.Task> list(Long userId, String scope, int page, int pageSize) {
        Rider rider = riderService.require(userId);
        int current = Math.max(1, page);
        int size = Math.min(50, Math.max(1, pageSize));
        LambdaQueryWrapper<Delivery> filter = filter(rider.getId(), scope);
        long total = deliveries.selectCount(filter);
        List<Delivery> rows = deliveries.selectList(filter(rider.getId(), scope)
                .orderByDesc(Delivery::getId).last("LIMIT " + size + " OFFSET " + (long) (current - 1) * size));
        return PageDto.of(rows.stream().map(row -> view(row, orders.find(row.getOrderId()), rider.getId())).toList(),
                total, current, size);
    }

    @Transactional
    public DeliveryDto.Task accept(Long userId, Long id) {
        Rider rider = riderService.lock(userId);
        Delivery delivery = locked(id);
        if (Objects.equals(delivery.getRiderId(), rider.getId()) && !"WAITING".equals(delivery.getStatus())) {
            return view(delivery, orders.find(delivery.getOrderId()), rider.getId());
        }
        if (rider.getStatus() != 1) {
            throw new BizException(ErrorCode.INVALID_PARAM, "休息中，请先开启接单");
        }
        if (!"WAITING".equals(delivery.getStatus()) || delivery.getRiderId() != null) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED, "该配送单已被其他配送员接走");
        }
        Order order = orders.requireReady(delivery.getOrderId());
        delivery.setRiderId(rider.getId());
        delivery.setStatus("ACCEPTED");
        delivery.setAcceptedAt(clock.now());
        deliveries.updateById(delivery);
        return view(delivery, order, rider.getId());
    }

    @Transactional
    public DeliveryDto.Task pick(Long userId, Long id) {
        Rider rider = riderService.lock(userId);
        Delivery delivery = owned(rider, id);
        if ("PICKED".equals(delivery.getStatus()) || "DELIVERED".equals(delivery.getStatus())) {
            return view(delivery, orders.find(delivery.getOrderId()), rider.getId());
        }
        expect(delivery, "ACCEPTED");
        Order order = orders.requireReady(delivery.getOrderId());
        orders.picked(order, userId, rider.getRealName());
        delivery.setStatus("PICKED");
        delivery.setPickedAt(clock.now());
        deliveries.updateById(delivery);
        return view(delivery, order, rider.getId());
    }

    @Transactional
    public DeliveryDto.Task deliver(Long userId, Long id) {
        Rider rider = riderService.lock(userId);
        Delivery delivery = owned(rider, id);
        if ("DELIVERED".equals(delivery.getStatus())) {
            return view(delivery, orders.find(delivery.getOrderId()), rider.getId());
        }
        expect(delivery, "PICKED");
        Order order = orders.requireReady(delivery.getOrderId());
        orders.delivered(order, userId, rider.getRealName());
        delivery.setStatus("DELIVERED");
        delivery.setDeliveredAt(clock.now());
        deliveries.updateById(delivery);
        riders.credit(rider.getId(), delivery.getRiderIncome());
        return view(delivery, order, rider.getId());
    }

    private LambdaQueryWrapper<Delivery> filter(Long riderId, String scope) {
        LambdaQueryWrapper<Delivery> wrapper = new LambdaQueryWrapper<>();
        return switch (scope) {
            case "POOL" -> wrapper.eq(Delivery::getStatus, "WAITING").isNull(Delivery::getRiderId);
            case "ACTIVE" -> wrapper.eq(Delivery::getRiderId, riderId).in(Delivery::getStatus, "ACCEPTED", "PICKED");
            case "HISTORY" -> wrapper.eq(Delivery::getRiderId, riderId).in(Delivery::getStatus, "DELIVERED", "FAILED");
            default -> throw new BizException(ErrorCode.INVALID_PARAM, "配送列表类型不正确");
        };
    }

    private Delivery locked(Long id) {
        Delivery row = deliveries.lock(id);
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "配送单不存在");
        }
        return row;
    }

    private Delivery owned(Rider rider, Long id) {
        Delivery row = locked(id);
        if (!Objects.equals(row.getRiderId(), rider.getId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己接取的配送单");
        }
        return row;
    }

    private void expect(Delivery delivery, String status) {
        if (!status.equals(delivery.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, "请按接单、取餐、送达顺序操作");
        }
    }

    private DeliveryDto.Task view(Delivery delivery, Order order, Long riderId) {
        boolean owned = Objects.equals(delivery.getRiderId(), riderId);
        boolean completed = "DELIVERED".equals(delivery.getStatus()) || "FAILED".equals(delivery.getStatus());
        boolean privateDetails = owned && !completed;
        List<DeliveryDto.Item> items = owned ? orders.items(order.getId()).stream().map(item ->
                new DeliveryDto.Item(item.getDishName(), Ids.blank(item.getOptionText()), item.getQuantity())).toList() : List.of();
        return new DeliveryDto.Task(Ids.of(delivery.getId()), order.getOrderNo(), delivery.getStatus(),
                Ids.blank(order.getMerchantNameSnapshot()), Ids.blank(order.getPickupPointNameSnapshot()),
                Ids.blank(delivery.getTargetBuilding()), privateDetails ? Ids.blank(order.getDormAddress()) : "",
                privateDetails ? Ids.blank(order.getContactName()) : "", privateDetails ? Ids.blank(order.getContactPhone()) : "",
                privateDetails ? order.getPickupCode() : null, privateDetails ? Ids.blank(order.getRemark()) : "",
                order.getItemCount(), delivery.getRiderIncome(), delivery.getCreatedAt(), delivery.getAcceptedAt(),
                delivery.getPickedAt(), delivery.getDeliveredAt(), items);
    }
}
