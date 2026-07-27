package com.swp391.api.modules.promotion.controller;

import com.swp391.api.modules.account.dto.PageResponse;
import com.swp391.api.modules.promotion.dto.PromotionRequest;
import com.swp391.api.modules.promotion.dto.PromotionResponse;
import com.swp391.api.modules.promotion.dto.PromotionStatusRequest;
import com.swp391.api.modules.promotion.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PromotionResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ALL") String status
    ) {
        return ResponseEntity.ok(promotionService.getPage(page, size, search, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<PromotionResponse> create(@Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromotionResponse> update(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(promotionService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PromotionResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody PromotionStatusRequest request
    ) {
        return ResponseEntity.ok(promotionService.updateStatus(id, request.isActive()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        promotionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
