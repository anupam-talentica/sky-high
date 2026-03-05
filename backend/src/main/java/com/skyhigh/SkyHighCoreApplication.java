package com.skyhigh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = RedisAutoConfiguration.class)
@EnableCaching
@EnableScheduling
public class SkyHighCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyHighCoreApplication.class, args);
    }
}
