package ru.nsg.atol.webserver.entety;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;

import java.util.Date;
import java.util.Objects;

public class Task{
    private String uuid;
    private String device;
    private String data;
    private boolean isReady;
    private boolean isCanceled;
    private Date timestamp;
    private String docNumber;

    public Task() {}

    public Task(String uuid) {
        this.uuid = uuid;
    }

    public Task(Any any) {
        uuid = any.get("uuid").toString();
        data = any.get("request").toString();
        timestamp = new Date();
        if (any.keys().contains("docNumber")){
            docNumber = any.get("docNumber").toString();
        }

        String devKeyName = any.get("device").toString().isEmpty() ? "unit" : "device";
        device = any.get(devKeyName).toString();
    }

    public String getUuid() {
        return uuid;
    }
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getData() {
        return data;
    }
    public void setData(String data) {
        this.data = data;
    }

    public Date getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isReady() {
        return isReady;
    }
    public void setReady(boolean ready) {
        isReady = ready;
    }

    public boolean isCanceled() {
        return isCanceled;
    }
    public void setCanceled(boolean canceled) {
        isCanceled = canceled;
    }

    public String getDevice() {
        return device;
    }
    public void setDevice(String device) {
        this.device = device;
    }

    public String getDocNumber() {
        return docNumber;
    }
    public void setDocNumber(String docNumber) {
        this.docNumber = docNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, device);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Task)) {
            return false;
        }
        Task other = (Task) object;
        return this.uuid.equals(other.uuid) && this.device.equals(other.device);
    }

    public Any getDataJson(){
        return JsonIterator.deserialize(getData());
    }
}
