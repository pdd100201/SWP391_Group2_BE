package com.swp391.api.modules.payment.config;

import com.swp391.api.modules.payment.service.SepayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SepayProperties.class)
public class PaymentConfig {
}
