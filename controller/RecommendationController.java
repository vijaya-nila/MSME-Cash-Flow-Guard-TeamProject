package com.msme.cashflowguard.controller;

import com.msme.cashflowguard.dto.RecommendationResponse;
import com.msme.cashflowguard.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{id}")
    public RecommendationResponse recommendations(@RequestAttribute("userId") Long userId, @PathVariable("id") Long id) {
        return recommendationService.getRecommendations(userId, id);
    }
}
