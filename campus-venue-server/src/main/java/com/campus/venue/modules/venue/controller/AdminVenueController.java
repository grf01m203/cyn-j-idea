package com.campus.venue.modules.venue.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.venue.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 场馆/场地/开放规则维护（管理员）
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SaCheckRole(value = {"VENUE_ADMIN", "SYS_ADMIN"}, mode = SaMode.OR)
public class AdminVenueController {

    private final VenueService venueService;

    @PostMapping("/venues")
    public R<Map<String, Object>> createVenue(@Valid @RequestBody VenueService.VenueDTO dto) {
        return R.ok(Map.of("id", venueService.createVenue(dto)));
    }

    @PutMapping("/venues/{id}")
    public R<Void> updateVenue(@PathVariable Long id, @Valid @RequestBody VenueService.VenueDTO dto) {
        venueService.updateVenue(id, dto);
        return R.ok();
    }

    @DeleteMapping("/venues/{id}")
    public R<Void> deleteVenue(@PathVariable Long id) {
        venueService.deleteVenue(id);
        return R.ok();
    }

    @PostMapping("/courts")
    public R<Map<String, Object>> createCourt(@Valid @RequestBody VenueService.CourtDTO dto) {
        return R.ok(Map.of("id", venueService.createCourt(dto)));
    }

    @PutMapping("/courts/{id}")
    public R<Void> updateCourt(@PathVariable Long id, @Valid @RequestBody VenueService.CourtDTO dto) {
        venueService.updateCourt(id, dto);
        return R.ok();
    }

    @DeleteMapping("/courts/{id}")
    public R<Void> deleteCourt(@PathVariable Long id) {
        venueService.deleteCourt(id);
        return R.ok();
    }

    @GetMapping("/open-rules")
    public R<List<Map<String, Object>>> rules(@RequestParam Long courtId) {
        return R.ok(venueService.rules(courtId));
    }

    @PutMapping("/courts/{id}/rules")
    public R<Void> saveRules(@PathVariable Long id, @Valid @RequestBody VenueService.RulesDTO dto) {
        venueService.saveRules(id, dto.rules());
        return R.ok();
    }
}
