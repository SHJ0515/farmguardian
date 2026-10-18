package com.farmguardian.farmguardian.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshTokenRequestDto {
    @NotBlank
    private String refreshToken;

    @NotBlank
    @Size(max = 255)
    private String clientUuid;
}
