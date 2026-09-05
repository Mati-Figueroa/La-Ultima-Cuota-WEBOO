package com.ultimacuota.controllers;

import com.ultimacuota.dto.ApiResponse;
import com.ultimacuota.dto.CreateAuctionRequest;
import com.ultimacuota.dto.BidRequest;
import com.ultimacuota.security.CustomUserDetails;
import com.ultimacuota.services.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getActiveAuctions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("auctions", auctionService.getActiveAuctions(search, sort))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getAuctionById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("auction", auctionService.getAuctionById(id))));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Object>> getMyAuctions(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("auctions", auctionService.getMyAuctions(user.getId()))));
    }

    @GetMapping("/my-bids")
    public ResponseEntity<ApiResponse<Object>> getMyBids(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("bids", auctionService.getMyBids(user.getId()))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> createAuction(
            @RequestBody CreateAuctionRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(auctionService.createAuction(request, user.getId())));
    }

    @PostMapping("/{id}/bid")
    public ResponseEntity<ApiResponse<Object>> placeBid(
            @PathVariable Long id,
            @RequestBody BidRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.placeBid(id, request, user.getId())));
    }
}
