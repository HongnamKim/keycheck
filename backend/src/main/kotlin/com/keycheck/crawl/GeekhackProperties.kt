package com.keycheck.crawl

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("keycheck.geekhack")
data class GeekhackProperties(
    val baseUrl: String,
    val userAgent: String,
    val minInterval: Duration,
)
