package com.keycheck.crawl

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import java.time.Clock

@Configuration
class GeekhackConfig {

    @Bean
    fun requestGate(properties: GeekhackProperties, clock: Clock) = RequestGate(properties.minInterval, clock)

    @Bean
    fun geekhackClient(properties: GeekhackProperties) = GeekhackClient(
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent)
            .build()
    )
}
