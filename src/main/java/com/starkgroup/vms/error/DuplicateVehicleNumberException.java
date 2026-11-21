package com.starkgroup.vms.error;

public class DuplicateVehicleNumberException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String vehicleNumber;

    public DuplicateVehicleNumberException(String vehicleNumber) {
        super("Vehicle number '" + vehicleNumber + "' already exists.");
        this.vehicleNumber = vehicleNumber;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }
}
