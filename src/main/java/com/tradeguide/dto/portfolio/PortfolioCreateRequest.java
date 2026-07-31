package com.tradeguide.dto.portfolio;

import jakarta.validation.constraints.NotBlank;

public class PortfolioCreateRequest {

    @NotBlank(message = "포트폴리오 이름은 필수입니다.")
    private final String name;

    public PortfolioCreateRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
