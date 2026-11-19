package com.starkgroup.vms.config;

import com.starkgroup.vms.vehicle.Vehicle;
import com.starkgroup.vms.vehicle.VehicleRepository;
import com.starkgroup.vms.vehicle.VehicleStatus;
import com.starkgroup.vms.vehicle.VehicleType;
import com.starkgroup.vms.user.User;
import com.starkgroup.vms.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the register with the bootstrap operator and a realistic Stark Group
 * fleet. Inserts only when the rows are missing, so it is safe on every start.
 *
 * <p>The operator password comes from {@code app.seed.admin-password}. The
 * local default is a convenience for development; production supplies its own
 * value through the environment.</p>
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminName;

    public DataSeeder(
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin-username:admin}") String adminUsername,
            @Value("${app.seed.admin-password:admin123}") String adminPassword,
            @Value("${app.seed.admin-name:Stark Group Administrator}") String adminName
    ) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }

    @Override
    public void run(String... args) {
        seedAdministrator();
        if (vehicleRepository.count() == 0) {
            long inserted = seedFleet();
            log.info("Seeded {} sample vehicles into the fleet register.", inserted);
        }
    }

    private void seedAdministrator() {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No bootstrap administrator password configured (app.seed.admin-password); "
                    + "skipping operator creation.");
            return;
        }
        if (!userRepository.existsByUsername(adminUsername)) {
            userRepository.save(new User(
                    adminUsername,
                    passwordEncoder.encode(adminPassword),
                    adminName,
                    "ADMIN"
            ));
            log.info("Seeded administrator account '{}'.", adminUsername);
        }
    }

    private long seedFleet() {
        for (String[] row : FLEET) {
            Vehicle vehicle = new Vehicle();
            vehicle.setVehicleNumber(row[0]);
            vehicle.setCompany(row[1]);
            vehicle.setType(VehicleType.valueOf(row[2]));
            vehicle.setBrand(row[3]);
            vehicle.setModel(row[4]);
            vehicle.setStatus(VehicleStatus.valueOf(row[5]));
            vehicleRepository.save(vehicle);
        }
        return vehicleRepository.count();
    }

    /** number, company, type, brand, model, status */
    private static final String[][] FLEET = {
            {"DHA-GA-11-1001", "Stark Group", "SUV", "Toyota", "Corolla Cross", "ACTIVE"},
            {"DHA-GA-11-1002", "Stark Construction", "PICKUP", "Toyota", "Hilux", "ACTIVE"},
            {"DHA-GA-12-1003", "Stark Construction", "PICKUP", "Nissan", "Navara", "MAINTENANCE"},
            {"DHA-GA-13-1004", "Stark Group", "SUV", "Mitsubishi", "Pajero Sport", "ACTIVE"},
            {"DHA-GA-14-1005", "Stark Properties", "SUV", "Honda", "CR-V", "ACTIVE"},
            {"DHA-GA-15-1006", "Stark Group", "VAN", "Toyota", "Hiace", "ACTIVE"},
            {"DHA-GA-16-1007", "Stark Retail", "VAN", "Hyundai", "H1", "INACTIVE"},
            {"DHA-GA-17-1008", "Stark Group", "SUV", "Toyota", "Land Cruiser", "ACTIVE"},
            {"DHA-GA-18-1009", "Stark Logistics", "TRUCK", "Isuzu", "NPR", "ACTIVE"},
            {"DHA-GA-19-1010", "Stark Logistics", "TRUCK", "Tata", "LPT 613", "MAINTENANCE"},
            {"DHA-GA-21-1011", "Stark Group", "BUS", "Toyota", "Coaster", "ACTIVE"},
            {"DHA-GA-22-1012", "Stark Logistics", "BUS", "Ashok Leyland", "Viking", "INACTIVE"},
            {"DHA-GA-23-1013", "Stark Retail", "SUV", "Suzuki", "Vitara", "ACTIVE"},
            {"DHA-GA-24-1014", "Stark Logistics", "PICKUP", "Mazda", "BT-50", "ACTIVE"},
            {"DHA-GA-25-1015", "Stark Energy", "SUV", "Kia", "Sportage", "ACTIVE"},
            {"DHA-GA-26-1016", "Stark Energy", "SUV", "Hyundai", "Tucson", "MAINTENANCE"},
            {"DHA-GA-27-1017", "Stark Construction", "CAR", "Toyota", "Aqua", "ACTIVE"},
            {"DHA-GA-28-1018", "Stark Properties", "CAR", "Honda", "Civic", "INACTIVE"},
            {"DHA-GA-29-1019", "Stark Group", "CAR", "Toyota", "Premio", "ACTIVE"},
            {"DHA-GA-31-1020", "Stark Retail", "CAR", "Nissan", "Sunny", "ACTIVE"},
            {"DHA-GA-32-1021", "Stark Group", "VAN", "Toyota", "Noah", "ACTIVE"},
            {"DHA-GA-33-1022", "Stark Construction", "PICKUP", "Ford", "Ranger", "ACTIVE"},
            {"DHA-GA-34-1023", "Stark Energy", "PICKUP", "Mitsubishi", "L200", "INACTIVE"},
            {"DHA-GA-35-1024", "Stark Group", "VAN", "Toyota", "Hiace", "ACTIVE"}
    };
}
