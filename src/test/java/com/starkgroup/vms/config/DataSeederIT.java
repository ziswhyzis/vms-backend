package com.starkgroup.vms.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.starkgroup.vms.user.User;
import com.starkgroup.vms.user.UserRepository;
import com.starkgroup.vms.vehicle.Vehicle;
import com.starkgroup.vms.vehicle.VehicleRepository;
import com.starkgroup.vms.vehicle.VehicleStatus;
import com.starkgroup.vms.vehicle.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * The demo register is populated into SQL by the seeder, never by the client.
 * These tests pin the sample data down: size, Bangladeshi plate shape and
 * idempotent re-runs.
 */
@SpringBootTest
@ActiveProfiles("test")
class DataSeederIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        vehicleRepository.deleteAll();
        userRepository.deleteAll();
    }

    private DataSeeder seeder(String password) {
        return new DataSeeder(
                userRepository,
                vehicleRepository,
                passwordEncoder,
                "admin",
                password,
                "Stark Group Administrator"
        );
    }

    @Test
    void seedsFortyEightBangladeshiVehicles() {
        seeder("seed-passphrase").run();

        assertThat(vehicleRepository.count()).isEqualTo(48);
        assertThat(vehicleRepository.findAll())
                .allSatisfy(vehicle -> assertThat(vehicle.getVehicleNumber())
                        .matches("^[A-Z]{3}-[A-Z]{2}-\\d{2}-\\d{4}$"));
    }

    @Test
    void spreadsTheFleetAcrossBangladeshiCities() {
        seeder("seed-passphrase").run();

        assertThat(vehicleRepository.findAll())
                .extracting(vehicle -> vehicle.getVehicleNumber().substring(0, 3))
                .contains("DHA", "CTG", "SYL", "KHL", "RAJ", "BAR", "RNG", "MYM");
    }

    @Test
    void sampleRowCarriesTheExpectedFields() {
        seeder("seed-passphrase").run();

        Vehicle vehicle = vehicleRepository.findByVehicleNumberIgnoreCase("CTG-GA-11-2001").orElseThrow();
        assertThat(vehicle.getCompany()).isEqualTo("Stark Energy");
        assertThat(vehicle.getType()).isEqualTo(VehicleType.SUV);
        assertThat(vehicle.getBrand()).isEqualTo("Kia");
        assertThat(vehicle.getModel()).isEqualTo("Seltos");
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ACTIVE);
    }

    @Test
    void rerunningAddsNothingAndKeepsTheRegisterStable() {
        DataSeeder seeder = seeder("seed-passphrase");
        seeder.run();
        seeder.run();

        assertThat(vehicleRepository.count()).isEqualTo(48);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void existingRowsAreNeverOverwritten() {
        Vehicle own = new Vehicle();
        own.setVehicleNumber("DHA-GA-11-1001");
        own.setCompany("Stark Group");
        own.setType(VehicleType.VAN);
        own.setBrand("Toyota");
        own.setModel("HiAce Super GL");
        own.setStatus(VehicleStatus.INACTIVE);
        vehicleRepository.save(own);

        seeder("seed-passphrase").run();

        assertThat(vehicleRepository.count()).isEqualTo(48);
        Vehicle kept = vehicleRepository.findByVehicleNumberIgnoreCase("DHA-GA-11-1001").orElseThrow();
        assertThat(kept.getModel()).isEqualTo("HiAce Super GL");
        assertThat(kept.getStatus()).isEqualTo(VehicleStatus.INACTIVE);
    }

    @Test
    void createsAnAdminWithABcryptPassword() {
        seeder("seed-passphrase").run();

        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("seed-passphrase", admin.getPasswordHash())).isTrue();
    }

    @Test
    void skipsOperatorCreationWhenNoPasswordIsConfigured() {
        seeder("   ").run();

        assertThat(userRepository.count()).isZero();
        assertThat(vehicleRepository.count()).isEqualTo(48);
    }
}
