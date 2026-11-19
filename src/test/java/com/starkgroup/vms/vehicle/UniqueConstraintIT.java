package com.starkgroup.vms.vehicle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the duplicate Vehicle Number rule is enforced by the SQL database itself,
 * not only by the friendly service-layer check.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UniqueConstraintIT {

    @Autowired
    private VehicleRepository vehicleRepository;

    private Vehicle vehicle(String number) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleNumber(number);
        vehicle.setCompany("Stark Construction");
        vehicle.setType(VehicleType.PICKUP);
        vehicle.setBrand("Toyota");
        vehicle.setModel("Hilux");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        return vehicle;
    }

    @Test
    void uniqueIndexRejectsSecondVehicleWithSameNumber() {
        vehicleRepository.saveAndFlush(vehicle("DHA-GA-11-3001"));

        assertThatThrownBy(() -> vehicleRepository.saveAndFlush(vehicle("DHA-GA-11-3001")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
