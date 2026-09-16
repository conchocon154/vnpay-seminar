package com.seminar.vnpay;

import com.seminar.vnpay.config.VnpayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(VnpayProperties.class)
public class VnpayDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(VnpayDemoApplication.class, args);
    }
}
