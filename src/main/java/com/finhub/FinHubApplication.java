package com.finhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FinHub 个人资金数据治理中台
 * DDD 分层架构：接口层 → 应用层 → 领域层 → 基础设施层
 */
@SpringBootApplication
public class FinHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinHubApplication.class, args);
    }
}