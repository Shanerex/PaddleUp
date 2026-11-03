package com.paddleup.packs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under {@code paddleup.packs}. {@code dir} is the directory holding the pack files. */
@ConfigurationProperties("paddleup.packs")
public record PacksProperties(@DefaultValue("../packs") String dir) {}
