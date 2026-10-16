package com.msme.cashflowguard.controller;

import com.msme.cashflowguard.dto.ForecastResponse;
import com.msme.cashflowguard.service.ForecastService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping("/{id}")
    public ForecastResponse forecast(@RequestAttribute("userId") Long userId, @PathVariable("id") Long id) {
        return forecastService.getForecast(userId, id);
    }
}
