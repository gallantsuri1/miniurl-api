package com.miniurl.url;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class UrlServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UrlServiceApplication.class, args);
    }
}
