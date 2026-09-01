package com.tradeguide.controller.asset;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.asset.AssetListingResponse;
import com.tradeguide.service.asset.AssetListingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetListingController {

    private final AssetListingService assetListingService;

    public AssetListingController(AssetListingService assetListingService) {
        this.assetListingService = assetListingService;
    }

    @GetMapping
    public List<AssetListingResponse> searchAssetListings(
            @RequestParam Market market,
            @RequestParam(defaultValue = "") String query
    ) {
        return assetListingService.searchActiveListings(market, query)
                .stream()
                .map(AssetListingResponse::from)
                .toList();
    }
}
