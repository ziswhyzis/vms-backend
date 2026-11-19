package com.starkgroup.vms.vehicle;

import java.time.Instant;

public record VehicleResponse(
        Long id,
        String vehicleNumber,
        String company,
        VehicleType type,
        String brand,
        String model,
        VehicleStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getVehicleNumber(),
                vehicle.getCompany(),
                vehicle.getType(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getStatus(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }
}
