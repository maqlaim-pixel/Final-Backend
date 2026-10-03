package com.travelvista.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public class LocalTravelEnquiryRequest {
    @NotBlank
    @Pattern(regexp = "airport-transfer|railway-station-transfer|full-day-city-tour|half-day-city-tour|outstation-cab|car-rental|local-taxi-cab|corporate-transportation")
    private String serviceType;

    @jakarta.validation.constraints.NotNull
    @Size(max = 40)
    private Map<@NotBlank @Size(max = 80) String, @jakarta.validation.constraints.NotNull @Size(max = 2000) String> formData;

    @Size(max = 500)
    private String sourceUrl;

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Map<String, String> getFormData() { return formData; }
    public void setFormData(Map<String, String> formData) { this.formData = formData; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
}
