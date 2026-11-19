package com.starkgroup.vms.vehicle;

import com.starkgroup.vms.common.PageResponse;
import com.starkgroup.vms.error.BadRequestException;
import com.starkgroup.vms.error.DuplicateVehicleNumberException;
import com.starkgroup.vms.error.ResourceNotFoundException;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    /** Fields a client is allowed to sort by. Guards against sorting on arbitrary/unsafe columns. */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "vehicleNumber", "company", "type", "brand", "model", "status", "createdAt", "updatedAt"
    );

    private static final int MAX_PAGE_SIZE = 100;

    private final VehicleRepository repository;

    public VehicleService(VehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> list(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sort));
        Page<VehicleResponse> result = repository.findAll(pageable).map(VehicleResponse::from);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(Long id) {
        return VehicleResponse.from(findVehicle(id));
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        String vehicleNumber = normalize(request.vehicleNumber());
        if (repository.existsByVehicleNumberIgnoreCase(vehicleNumber)) {
            throw new DuplicateVehicleNumberException(vehicleNumber);
        }
        Vehicle vehicle = new Vehicle();
        apply(vehicle, request, vehicleNumber);
        return VehicleResponse.from(saveGuardingUniqueness(vehicle, vehicleNumber));
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = findVehicle(id);
        String vehicleNumber = normalize(request.vehicleNumber());
        if (repository.existsByVehicleNumberIgnoreCaseAndIdNot(vehicleNumber, id)) {
            throw new DuplicateVehicleNumberException(vehicleNumber);
        }
        apply(vehicle, request, vehicleNumber);
        return VehicleResponse.from(saveGuardingUniqueness(vehicle, vehicleNumber));
    }

    @Transactional
    public void delete(Long id) {
        Vehicle vehicle = findVehicle(id);
        repository.delete(vehicle);
    }

    private Vehicle saveGuardingUniqueness(Vehicle vehicle, String vehicleNumber) {
        try {
            return repository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException ex) {
            // The database unique index is the final guarantee: translate it into the business error.
            throw new DuplicateVehicleNumberException(vehicleNumber);
        }
    }

    private Vehicle findVehicle(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle with id " + id + " was not found."));
    }

    private void apply(Vehicle vehicle, VehicleRequest request, String vehicleNumber) {
        vehicle.setVehicleNumber(vehicleNumber);
        vehicle.setCompany(request.company().trim());
        vehicle.setType(request.type());
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setStatus(request.status() == null ? VehicleStatus.ACTIVE : request.status());
    }

    /** Vehicle numbers are case-insensitive and whitespace-insensitive in the Stark Group registry. */
    private String normalize(String vehicleNumber) {
        return vehicleNumber == null
                ? ""
                : vehicleNumber.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "vehicleNumber");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new BadRequestException(
                    "Unsupported sort field '" + field + "'. Allowed fields: "
                            + String.join(", ", new TreeSet<>(SORTABLE_FIELDS)) + ".");
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }
}
