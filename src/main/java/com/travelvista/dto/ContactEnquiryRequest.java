package com.travelvista.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ContactEnquiryRequest {
    @NotBlank @Size(max = 150)
    private String name;
    @NotBlank @Email @Size(max = 150)
    private String email;
    @NotBlank @Pattern(regexp = "^[+0-9][0-9\\s()\\-]{6,29}$")
    private String phone;
    @NotBlank @Size(max = 100)
    private String subject;
    @NotBlank @Size(max = 10000)
    private String message;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
