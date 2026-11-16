package com.campus.smartorder;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
@ConfigurationPropertiesScan
@MapperScan("com.campus.smartorder.**.mapper")
public class SmartOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartOrderApplication.class, args);
    }
}
