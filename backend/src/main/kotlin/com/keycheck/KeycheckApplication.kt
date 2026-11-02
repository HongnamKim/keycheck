package com.keycheck

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class KeycheckApplication

fun main(args: Array<String>) {
    runApplication<KeycheckApplication>(*args)
}
