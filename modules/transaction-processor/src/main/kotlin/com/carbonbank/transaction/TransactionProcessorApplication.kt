package com.carbonbank.transaction

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TransactionProcessorApplication

fun main(args: Array<String>) {
    runApplication<TransactionProcessorApplication>(*args)
}
