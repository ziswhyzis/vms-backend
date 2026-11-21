package com.starkgroup.vms.vehicle;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    boolean existsByVehicleNumberIgnoreCase(String vehicleNumber);

    boolean existsByVehicleNumberIgnoreCaseAndIdNot(String vehicleNumber, Long id);

    Optional<Vehicle> findByVehicleNumberIgnoreCase(String vehicleNumber);
}
