package com.keycheck.crawl;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("keycheck.geekhack")
public record GeekhackProperties(String baseUrl, String userAgent, Duration minInterval) {}
