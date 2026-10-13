package com.neit.personal_finance.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 64)
    private String username;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    private String fullName;

    @NotBlank
    @Size(max = 254)
    @Email
    private String email;

    private String phone;

}
