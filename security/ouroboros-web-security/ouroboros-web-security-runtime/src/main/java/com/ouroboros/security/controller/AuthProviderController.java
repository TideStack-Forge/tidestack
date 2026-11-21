package com.ouroboros.security.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.security.externalauth.ExternalAuthProviderInfoService;
import com.ouroboros.web.WebApiResult;

/**
 * Provider listing endpoint for external auth.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthProviderController {

    @Autowired
    private ExternalAuthProviderInfoService externalAuthProviderInfoService;

    @Anonymous
    @Operation(
        summary = "获取可用外部认证提供方",
        description = "返回当前运行时可用的外部认证提供方列表及其展示信息。",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回外部认证提供方列表")
    })
    @GetMapping("/providers")
    public WebApiResult<?> listProviders() {
        return WebApiResult.ok(externalAuthProviderInfoService.listProviders());
    }
}
