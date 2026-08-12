package com.tradeguide.dto.portfolio;

import com.tradeguide.domain.portfolio.Portfolio;

public class PortfolioResponse {

    private final Long id;
    private final String name;

    private PortfolioResponse(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName()
        );
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
