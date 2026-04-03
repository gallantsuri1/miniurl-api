package com.miniurl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AppConfig {

    @Value("${app.name:MiniURL}")
    private String appName;

    @ModelAttribute("appName")
    public String populateAppName() {
        return appName;
    }
}
