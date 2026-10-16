package com.msme.cashflowguard.controller;

import com.msme.cashflowguard.dto.StressResult;
import com.msme.cashflowguard.service.StressScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stress")
public class StressController {

    private final StressScoreService stressService;

    public StressController(StressScoreService stressService) {
        this.stressService = stressService;
    }

    @GetMapping("/{id}")
    public StressResult stress(@RequestAttribute("userId") Long userId, @PathVariable("id") Long id) {
        return stressService.getStress(userId, id);
    }
}
