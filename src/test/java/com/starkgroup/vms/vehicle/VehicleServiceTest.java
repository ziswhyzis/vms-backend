package com.starkgroup.vms.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.starkgroup.vms.common.PageResponse;
import com.starkgroup.vms.error.BadRequestException;
import com.starkgroup.vms.error.DuplicateVehicleNumberException;
import com.starkgroup.vms.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository repository;

    private VehicleService service;

    @BeforeEach
    void setUp() {
        service = new VehicleService(repository);
    }

    private VehicleRequest request(String number) {
        return new VehicleRequest(
                number, "Stark Construction", VehicleType.PICKUP, "Toyota", "Hilux", VehicleStatus.ACTIVE
        );
    }

    private Vehicle vehicle(Long id, String number) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setVehicleNumber(number);
        vehicle.setCompany("Stark Construction");
        vehicle.setType(VehicleType.PICKUP);
        vehicle.setBrand("Toyota");
        vehicle.setModel("Hilux");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        return vehicle;
    }

    @Test
    void createNormalizesVehicleNumberToUpperCase() {
        when(repository.existsByVehicleNumberIgnoreCase("DHA-GA-11-2001")).thenReturn(false);
        when(repository.saveAndFlush(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = service.create(request("  dha-ga-11-2001  "));

        assertThat(response.vehicleNumber()).isEqualTo("DHA-GA-11-2001");
        verify(repository, never()).findByVehicleNumberIgnoreCase(any());
    }

    @Test
    void createRejectsDuplicateVehicleNumber() {
        when(repository.existsByVehicleNumberIgnoreCase("DHA-GA-11-2001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("DHA-GA-11-2001")))
                .isInstanceOf(DuplicateVehicleNumberException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).saveAndFlush(any(Vehicle.class));
    }

    @Test
    void createTranslatesDatabaseUniqueViolationToBusinessError() {
        when(repository.existsByVehicleNumberIgnoreCase("DHA-GA-11-2001")).thenReturn(false);
        when(repository.saveAndFlush(any(Vehicle.class)))
                .thenThrow(new DataIntegrityViolationException("unique index violated"));

        assertThatThrownBy(() -> service.create(request("DHA-GA-11-2001")))
                .isInstanceOf(DuplicateVehicleNumberException.class)
                .hasMessageContaining("DHA-GA-11-2001");
    }

    @Test
    void createCopiesEveryVehicleField() {
        when(repository.existsByVehicleNumberIgnoreCase("DHA-GA-11-2001")).thenReturn(false);
        when(repository.saveAndFlush(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = service.create(
                new VehicleRequest("DHA-GA-11-2001", " Stark Logistics ", VehicleType.TRUCK,
                        " Isuzu ", " NPR ", null));

        assertThat(response.company()).isEqualTo("Stark Logistics");
        assertThat(response.type()).isEqualTo(VehicleType.TRUCK);
        assertThat(response.brand()).isEqualTo("Isuzu");
        assertThat(response.model()).isEqualTo("NPR");
        assertThat(response.status()).isEqualTo(VehicleStatus.ACTIVE);
    }

    @Test
    void updateAllowsKeepingTheSameVehicleNumber() {
        Vehicle existing = vehicle(7L, "DHA-GA-11-2001");
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.existsByVehicleNumberIgnoreCaseAndIdNot("DHA-GA-11-2001", 7L)).thenReturn(false);
        when(repository.saveAndFlush(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = service.update(7L, request("dha-ga-11-2001"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.vehicleNumber()).isEqualTo("DHA-GA-11-2001");
    }

    @Test
    void updateRejectsNumberOwnedByAnotherVehicle() {
        when(repository.findById(8L)).thenReturn(Optional.of(vehicle(8L, "DHA-GA-11-2002")));
        when(repository.existsByVehicleNumberIgnoreCaseAndIdNot("DHA-GA-11-2001", 8L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(8L, request("DHA-GA-11-2001")))
                .isInstanceOf(DuplicateVehicleNumberException.class);

        verify(repository, never()).saveAndFlush(any(Vehicle.class));
    }

    @Test
    void deleteMissingVehicleThrowsNotFound() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void listClampsPageSizeAndReturnsSpringPageShape() {
        Vehicle vehicle = vehicle(1L, "DHA-GA-11-1001");
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(vehicle), PageRequest.of(0, 100), 100));

        PageResponse<VehicleResponse> page = service.list(0, 1000, "vehicleNumber,asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.totalElements()).isEqualTo(100);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.last()).isTrue();
        assertThat(page.content()).hasSize(1);
    }

    @Test
    void listSortsDescendingWhenRequested() {
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PageResponse<VehicleResponse> page = service.list(0, 10, "brand,desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("brand").getDirection().isDescending()).isTrue();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void listRejectsUnknownSortField() {
        assertThatThrownBy(() -> service.list(0, 10, "passwordHash,asc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported sort field");
    }
}
