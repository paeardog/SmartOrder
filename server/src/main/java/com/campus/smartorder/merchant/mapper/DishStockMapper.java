package com.campus.smartorder.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.merchant.domain.DishStock;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface DishStockMapper extends BaseMapper<DishStock> {

    @Insert("""
            INSERT IGNORE INTO m_dish_stock (dish_id, biz_date, stock_total, stock_locked, stock_sold)
            VALUES (#{dishId}, #{bizDate}, #{stockTotal}, 0, 0)
            """)
    int ensureRow(@Param("dishId") Long dishId, @Param("bizDate") LocalDate bizDate, @Param("stockTotal") int stockTotal);

    @Update("""
            UPDATE m_dish_stock
               SET stock_locked = stock_locked + #{quantity}
             WHERE dish_id = #{dishId} AND biz_date = #{bizDate}
               AND stock_total >= stock_locked + stock_sold + #{quantity}
            """)
    int acquireLock(@Param("dishId") Long dishId, @Param("bizDate") LocalDate bizDate, @Param("quantity") int quantity);

    @Update("""
            UPDATE m_dish_stock
               SET stock_locked = stock_locked - #{quantity}
             WHERE dish_id = #{dishId} AND biz_date = #{bizDate}
               AND stock_locked >= #{quantity}
            """)
    int releaseLock(@Param("dishId") Long dishId, @Param("bizDate") LocalDate bizDate, @Param("quantity") int quantity);

    @Update("""
            UPDATE m_dish_stock
               SET stock_locked = stock_locked - #{quantity}, stock_sold = stock_sold + #{quantity}
             WHERE dish_id = #{dishId} AND biz_date = #{bizDate}
               AND stock_locked >= #{quantity}
            """)
    int commitLockToSold(@Param("dishId") Long dishId, @Param("bizDate") LocalDate bizDate, @Param("quantity") int quantity);

    @Update("""
            UPDATE m_dish_option
               SET stock = stock - #{quantity}
             WHERE id = #{optionId} AND stock IS NOT NULL AND stock >= #{quantity}
            """)
    int acquireOptionStock(@Param("optionId") Long optionId, @Param("quantity") int quantity);

    @Update("""
            UPDATE m_dish_option
               SET stock = stock + #{quantity}
             WHERE id = #{optionId} AND stock IS NOT NULL
            """)
    int releaseOptionStock(@Param("optionId") Long optionId, @Param("quantity") int quantity);
}
