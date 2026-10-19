package com.farmguardian.farmguardian.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignInRequestDto {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    @Size(max = 255)
    private String clientUuid;
}
