package com.msme.cashflowguard.controller;

import com.msme.cashflowguard.dto.BusinessDetailsRequest;
import com.msme.cashflowguard.dto.BusinessDetailsResponse;
import com.msme.cashflowguard.service.BusinessDetailService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-details")
public class BusinessDetailsController {

    private final BusinessDetailService service;

    public BusinessDetailsController(BusinessDetailService service) {
        this.service = service;
    }

    @PostMapping
    public BusinessDetailsResponse save(@RequestAttribute("userId") Long userId,
                                        @Valid @RequestBody BusinessDetailsRequest request) {
        return service.save(userId, request);
    }

    @GetMapping
    public BusinessDetailsResponse get(@RequestAttribute("userId") Long userId) {
        return service.get(userId);
    }
}
