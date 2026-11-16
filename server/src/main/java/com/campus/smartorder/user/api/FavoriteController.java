package com.campus.smartorder.user.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Payloads;
import com.campus.smartorder.user.service.FavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ApiResponse<List<FavoriteDto.Item>> list(HttpServletRequest request) {
        return ApiResponse.ok(favoriteService.list(currentUserId(request)));
    }

    @PostMapping
    public ApiResponse<List<FavoriteDto.Item>> add(HttpServletRequest request,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        return ApiResponse.ok(favoriteService.add(currentUserId(request),
                Payloads.text(payload, "targetType"), Payloads.text(payload, "targetId")));
    }

    @DeleteMapping
    public ApiResponse<List<FavoriteDto.Item>> remove(HttpServletRequest request,
                                                      @RequestParam(name = "targetType", required = false) String targetType,
                                                      @RequestParam(name = "targetId", required = false) String targetId) {
        return ApiResponse.ok(favoriteService.remove(currentUserId(request), targetType, targetId));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
