package com.campus.venue.modules.user.controller;

import com.campus.venue.common.result.R;
import com.campus.venue.modules.user.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record LoginDTO(@NotBlank(message = "用户名不能为空") String username,
                           @NotBlank(message = "密码不能为空") String password) {
    }

    public record AvatarDTO(@NotBlank(message = "头像地址不能为空") String url) {
    }

    @PostMapping("/login")
    public R<AuthService.LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto.username(), dto.password()));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        return R.ok(authService.me());
    }

    /** 更新当前用户头像（先调 /api/v1/files/upload 拿到 url） */
    @PutMapping("/avatar")
    public R<Map<String, Object>> updateAvatar(@Valid @RequestBody AvatarDTO dto) {
        return R.ok(authService.updateAvatar(
                cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong(), dto.url()));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }
}
