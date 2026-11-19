package com.starkgroup.vms.vehicle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VehicleRequest(

        @NotBlank(message = "Vehicle number is required")
        @Size(min = 4, max = 20, message = "Vehicle number must be 4-20 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9-]{4,20}$",
                message = "Vehicle number may only contain letters, numbers and dashes"
        )
        String vehicleNumber,

        @NotBlank(message = "Company is required")
        @Size(min = 2, max = 60, message = "Company must be 2-60 characters")
        String company,

        @NotNull(message = "Vehicle type is required")
        VehicleType type,

        @NotBlank(message = "Brand is required")
        @Size(min = 2, max = 40, message = "Brand must be 2-40 characters")
        String brand,

        @NotBlank(message = "Model is required")
        @Size(min = 2, max = 40, message = "Model must be 2-40 characters")
        String model,

        VehicleStatus status
) {
}
