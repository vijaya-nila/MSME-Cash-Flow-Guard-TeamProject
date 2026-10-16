package com.msme.cashflowguard.controller;

import com.msme.cashflowguard.dto.WhatIfRequest;
import com.msme.cashflowguard.dto.WhatIfResponse;
import com.msme.cashflowguard.service.WhatIfSimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final WhatIfSimulationService simulationService;

    public SimulationController(WhatIfSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    public WhatIfResponse simulate(@RequestAttribute("userId") Long userId, @Valid @RequestBody WhatIfRequest request) {
        return simulationService.simulate(userId, request);
    }
}
