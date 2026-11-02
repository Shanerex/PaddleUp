package com.paddleup.accounts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under {@code paddleup.email.resend}. The API key comes from configuration only. */
@ConfigurationProperties("paddleup.email.resend")
public record ResendProperties(
    @DefaultValue("") String apiKey,
    @DefaultValue("PaddleUp <onboarding@resend.dev>") String from,
    @DefaultValue("https://api.resend.com") String baseUrl) {}
