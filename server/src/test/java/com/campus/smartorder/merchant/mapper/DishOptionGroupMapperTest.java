package com.campus.smartorder.merchant.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.campus.smartorder.merchant.domain.DishOptionGroup;
import com.campus.smartorder.merchant.domain.OptionGroupType;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DishOptionGroupMapperTest {

    @ParameterizedTest
    @CsvSource({",false", "0,false", "1,true", "2,false"})
    void requiredSelectionKeepsExistingSemantics(Integer required, boolean expected) {
        DishOptionGroup group = new DishOptionGroup();
        group.setRequired(required);
        assertEquals(expected, group.requiresSelection());
    }

    @ParameterizedTest
    @CsvSource({"SPEC,0", "SPEC,1", "TOPPING,0", "TOPPING,1"})
    void insertAndUpdateBindRequiredAsInteger(OptionGroupType type, int required) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(DishOptionGroupMapper.class);
        DishOptionGroup group = new DishOptionGroup();
        group.setId(10L);
        group.setDishId(20L);
        group.setName("规格加料");
        group.setGroupType(type);
        group.setRequired(required);
        group.setMinSelect(required);
        group.setMaxSelect(1);
        group.setSortWeight(0);

        for (String method : List.of("insert", "updateById")) {
            Object parameter = method.equals("insert") ? group : Map.of("et", group);
            BoundSql sql = configuration.getMappedStatement(DishOptionGroupMapper.class.getName() + "." + method)
                    .getBoundSql(parameter);
            assertTrue(sql.getSql().contains("m_dish_option_group"));
            assertTrue(sql.getSql().contains("required"));
            String property = method.equals("insert") ? "required" : "et.required";
            assertTrue(sql.getParameterMappings().stream().map(ParameterMapping::getProperty).toList().contains(property));
            MetaObject meta = configuration.newMetaObject(parameter);
            assertEquals(Integer.class, meta.getGetterType(property));
            assertEquals(required, meta.getValue(property));
        }
    }
}
