package com.campus.smartorder.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Update("""
            UPDATE o_order
               SET status = 'PAID', paid_at = NOW(3), version = version + 1
             WHERE id = #{orderId} AND status = 'PENDING_PAYMENT'
            """)
    int casMarkPaid(@Param("orderId") Long orderId);

    @Update("""
            UPDATE o_order
               SET status = 'ACCEPTED', accepted_at = NOW(3), expect_take_at = #{expectTakeAt}, version = version + 1
             WHERE id = #{orderId} AND status = 'PAID'
            """)
    int casAccept(@Param("orderId") Long orderId, @Param("expectTakeAt") LocalDateTime expectTakeAt);

    @Update("""
            UPDATE o_order
               SET status = 'COOKING', version = version + 1
             WHERE id = #{orderId} AND status = 'ACCEPTED'
            """)
    int casStartCooking(@Param("orderId") Long orderId);

    @Update("""
            UPDATE o_order
               SET status = 'READY', ready_at = NOW(3), pickup_code = #{pickupCode},
                   pickup_deadline_at = #{pickupDeadlineAt}, version = version + 1
             WHERE id = #{orderId} AND status IN ('ACCEPTED', 'COOKING')
            """)
    int casMarkReady(@Param("orderId") Long orderId,
                     @Param("pickupCode") String pickupCode,
                     @Param("pickupDeadlineAt") LocalDateTime pickupDeadlineAt);

    @Update("""
            UPDATE o_order
               SET status = 'COMPLETED', taken_at = NOW(3), finished_at = NOW(3), version = version + 1
             WHERE id = #{orderId} AND status = 'READY'
            """)
    int casComplete(@Param("orderId") Long orderId);

    @Update("""
            UPDATE o_order
               SET status = 'CANCELLED', cancelled_at = NOW(3), cancel_reason = #{reason},
                   cancel_by = #{cancelBy}, version = version + 1
             WHERE id = #{orderId} AND status = #{fromStatus}
            """)
    int casCancel(@Param("orderId") Long orderId,
                  @Param("fromStatus") OrderStatus fromStatus,
                  @Param("reason") String reason,
                  @Param("cancelBy") String cancelBy);

    @Update("""
            UPDATE o_order
               SET status = 'CLOSED', cancelled_at = NOW(3), cancel_reason = #{reason},
                   cancel_by = #{cancelBy}, version = version + 1
             WHERE id = #{orderId} AND status = 'PENDING_PAYMENT'
            """)
    int casClose(@Param("orderId") Long orderId,
                 @Param("reason") String reason,
                 @Param("cancelBy") String cancelBy);

    @Update("""
            UPDATE o_order
               SET status = 'REJECTED', cancelled_at = NOW(3), cancel_reason = #{reason},
                   cancel_by = 'MERCHANT', version = version + 1
             WHERE id = #{orderId} AND status IN ('PAID', 'ACCEPTED', 'COOKING')
            """)
    int casReject(@Param("orderId") Long orderId, @Param("reason") String reason);

    @Update("""
            UPDATE o_order
               SET status = 'REFUNDING', version = version + 1
             WHERE id = #{orderId} AND status = #{fromStatus}
            """)
    int casToRefunding(@Param("orderId") Long orderId, @Param("fromStatus") OrderStatus fromStatus);

    @Update("""
            UPDATE o_order
               SET status = 'REFUNDED', finished_at = NOW(3), version = version + 1
             WHERE id = #{orderId} AND status = 'REFUNDING'
            """)
    int casToRefunded(@Param("orderId") Long orderId);

    @Update("""
            UPDATE o_order
               SET status = #{toStatus}, version = version + 1
             WHERE id = #{orderId} AND status = 'REFUNDING'
            """)
    int casRestoreFromRefunding(@Param("orderId") Long orderId, @Param("toStatus") OrderStatus toStatus);

    @Update("""
            UPDATE o_order
               SET expect_take_at = #{expectTakeAt}, version = version + 1
             WHERE id = #{orderId} AND version = #{version} AND status IN ('PAID', 'ACCEPTED', 'COOKING')
            """)
    int casUpdateExpectTakeAt(@Param("orderId") Long orderId,
                              @Param("version") Integer version,
                              @Param("expectTakeAt") LocalDateTime expectTakeAt);

    @Update("""
            UPDATE o_order
               SET expect_take_at = #{expectTakeAt}, pickup_deadline_at = #{pickupDeadlineAt}, version = version + 1
             WHERE id = #{orderId} AND status = 'PAID'
            """)
    int updateTakePlan(@Param("orderId") Long orderId,
                       @Param("expectTakeAt") LocalDateTime expectTakeAt,
                       @Param("pickupDeadlineAt") LocalDateTime pickupDeadlineAt);

    @Select("""
            SELECT id FROM o_order
             WHERE status = 'PENDING_PAYMENT' AND expire_at < #{now}
             ORDER BY expire_at
             LIMIT #{limit}
            """)
    List<Long> selectPayTimeoutIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
            SELECT id FROM o_order
             WHERE status = 'READY' AND pickup_deadline_at < #{now}
             ORDER BY pickup_deadline_at
             LIMIT #{limit}
            """)
    List<Long> selectPickupOverdueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(DISTINCT pickup_code) FROM o_order
             WHERE merchant_id = #{merchantId} AND biz_date = #{bizDate} AND pickup_code IS NOT NULL
            """)
    int countIssuedPickupCodes(@Param("merchantId") Long merchantId, @Param("bizDate") LocalDate bizDate);

    @Select("SELECT * FROM o_order WHERE user_id = #{userId} AND client_order_no = #{clientOrderNo} LIMIT 1")
    Order selectByClientOrderNo(@Param("userId") Long userId, @Param("clientOrderNo") String clientOrderNo);
}
