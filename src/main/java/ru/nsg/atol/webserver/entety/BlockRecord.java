package ru.nsg.atol.webserver.entety;

public class BlockRecord{
    private String uuid;
    private String device;
    private long documentNumber;

    public BlockRecord(){}

    public BlockRecord(String uuid, long documentNumber, String device) {
        this.uuid = uuid;
        this.documentNumber = documentNumber;
        this.device = device;
    }

    public String getUuid() {
        return uuid;
    }
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getDevice() {
        return device;
    }
    public void setDevice(String device) {
        this.device = device;
    }

    public long getDocumentNumber() {
        return documentNumber;
    }
    public void setDocumentNumber(long documentNumber) {
        this.documentNumber = documentNumber;
    }
}
