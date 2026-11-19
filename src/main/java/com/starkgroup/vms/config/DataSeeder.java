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
 * Seeds the register with the bootstrap operator and a sample Bangladesh fleet.
 * Rows are inserted only when their vehicle number is missing, so the seeder is
 * safe on every start and an existing deployment picks up newly added vehicles
 * without a database wipe.
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
        long inserted = seedFleet();
        if (inserted > 0) {
            log.info("Seeded {} vehicles; the register now holds {}.", inserted, vehicleRepository.count());
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

    /** Adds only the sample vehicles that are not in the register yet. */
    private long seedFleet() {
        long inserted = 0;
        for (String[] row : FLEET) {
            if (vehicleRepository.existsByVehicleNumberIgnoreCase(row[0])) {
                continue;
            }
            Vehicle vehicle = new Vehicle();
            vehicle.setVehicleNumber(row[0]);
            vehicle.setCompany(row[1]);
            vehicle.setType(VehicleType.valueOf(row[2]));
            vehicle.setBrand(row[3]);
            vehicle.setModel(row[4]);
            vehicle.setStatus(VehicleStatus.valueOf(row[5]));
            vehicleRepository.save(vehicle);
            inserted++;
        }
        return inserted;
    }

    /**
     * 48 vehicles registered across Bangladesh. Numbers follow the national
     * plate shape {@code CITY-SERIES-NN-NNNN}: DHA Dhaka, CTG Chattogram,
     * SYL Sylhet, KHL Khulna, RAJ Rajshahi, BAR Barishal, RNG Rangpur,
     * MYM Mymensingh.
     *
     * <p>number, company, type, brand, model, status</p>
     */
    private static final String[][] FLEET = {
            // Dhaka
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
            {"DHA-GA-35-1024", "Stark Group", "VAN", "Toyota", "Hiace", "ACTIVE"},

            // Chattogram
            {"CTG-GA-11-2001", "Stark Energy", "SUV", "Kia", "Seltos", "ACTIVE"},
            {"CTG-GA-12-2002", "Stark Energy", "SUV", "Hyundai", "Creta", "ACTIVE"},
            {"CTG-GA-13-2003", "Stark Construction", "TRUCK", "Hino", "500 Series", "ACTIVE"},
            {"CTG-GA-14-2004", "Stark Group", "CAR", "Toyota", "Allion", "ACTIVE"},
            {"CTG-GA-15-2005", "Stark Retail", "CAR", "Toyota", "Axio", "ACTIVE"},
            {"CTG-GA-16-2006", "Stark Logistics", "BUS", "Hyundai", "Universe", "ACTIVE"},

            // Sylhet
            {"SYL-GA-11-2007", "Stark Agro", "PICKUP", "Toyota", "Hilux", "ACTIVE"},
            {"SYL-GA-12-2008", "Stark Agro", "PICKUP", "Mahindra", "Scorpio", "ACTIVE"},
            {"SYL-GA-13-2009", "Stark Group", "SUV", "Toyota", "Fortuner", "ACTIVE"},
            {"SYL-GA-14-2010", "Stark Retail", "CAR", "Suzuki", "Swift", "ACTIVE"},

            // Khulna
            {"KHL-GA-11-2011", "Stark Logistics", "TRUCK", "Eicher", "Pro 3015", "ACTIVE"},
            {"KHL-GA-12-2012", "Stark Properties", "CAR", "Toyota", "Premio", "ACTIVE"},
            {"KHL-GA-13-2013", "Stark Construction", "PICKUP", "Ford", "Ranger", "ACTIVE"},
            {"KHL-GA-14-2014", "Stark Group", "VAN", "Toyota", "Hiace", "MAINTENANCE"},

            // Rajshahi
            {"RAJ-GA-11-2015", "Stark Agro", "TRUCK", "Isuzu", "NQR", "ACTIVE"},
            {"RAJ-GA-12-2016", "Stark Textiles", "VAN", "Nissan", "Urvan", "ACTIVE"},
            {"RAJ-GA-13-2017", "Stark Textiles", "CAR", "Toyota", "Aqua", "ACTIVE"},
            {"RAJ-GA-14-2018", "Stark Agro", "PICKUP", "Mitsubishi", "L200", "INACTIVE"},

            // Barishal
            {"BAR-GA-11-2019", "Stark Group", "SUV", "Toyota", "Harrier", "ACTIVE"},
            {"BAR-GA-12-2020", "Stark Retail", "CAR", "Honda", "Vezel", "ACTIVE"},
            {"BAR-GA-13-2021", "Stark Logistics", "TRUCK", "Tata", "LPT 709", "ACTIVE"},

            // Rangpur
            {"RNG-GA-11-2022", "Stark Agro", "PICKUP", "Toyota", "Hilux", "INACTIVE"},
            {"RNG-GA-12-2023", "Stark Construction", "TRUCK", "Ashok Leyland", "Ecomet", "ACTIVE"},

            // Mymensingh
            {"MYM-GA-11-2024", "Stark Textiles", "VAN", "Toyota", "Hiace", "ACTIVE"}
    };
}
