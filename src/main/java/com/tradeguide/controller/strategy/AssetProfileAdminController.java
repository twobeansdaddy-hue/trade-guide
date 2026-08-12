package com.tradeguide.controller.strategy;

import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.dto.strategy.AssetProfileCreateRequest;
import com.tradeguide.dto.strategy.AssetProfileResponse;
import com.tradeguide.service.strategy.AssetProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/asset-profiles")
public class AssetProfileAdminController {

    private final AssetProfileService assetProfileService;

    public AssetProfileAdminController(AssetProfileService assetProfileService) {
        this.assetProfileService = assetProfileService;
    }

    @PostMapping
    public ResponseEntity<AssetProfileResponse> createAssetProfile(
            @Valid @RequestBody AssetProfileCreateRequest request
    ) {

        AssetProfile assetProfile = assetProfileService.createAssetProfile(
                request.getMarket(),
                request.getTicker(),
                request.getInvestmentTrack()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AssetProfileResponse.from(assetProfile));
    }

    @GetMapping
    public List<AssetProfileResponse> getAssetProfiles() {
        return assetProfileService.getAssetProfiles().stream()
                .map(AssetProfileResponse::from)
                .toList();
    }
}
