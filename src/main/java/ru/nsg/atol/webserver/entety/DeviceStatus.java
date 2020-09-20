package ru.nsg.atol.webserver.entety;

import java.util.Objects;

public class DeviceStatus {
    private String name;
    private boolean isDeviceBlocked, isPrinterOverheat;

    public DeviceStatus(String name, boolean isDeviceBlocked, boolean isPrinterOverheat) {
        this.name = name;
        this.isDeviceBlocked = isDeviceBlocked;
        this.isPrinterOverheat = isPrinterOverheat;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public boolean isDeviceBlocked() {
        return isDeviceBlocked;
    }
    public void setDeviceBlocked(boolean deviceBlocked) {
        isDeviceBlocked = deviceBlocked;
    }

    public boolean isPrinterOverheat() {
        return isPrinterOverheat;
    }
    public void setPrinterOverheat(boolean printerOverheat) {
        isPrinterOverheat = printerOverheat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceStatus)) return false;
        DeviceStatus that = (DeviceStatus) o;
        return isDeviceBlocked == that.isDeviceBlocked &&
                isPrinterOverheat == that.isPrinterOverheat &&
                name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, isDeviceBlocked, isPrinterOverheat);
    }
}
