package ru.nsg.atol.webserver.entety;

import com.jsoniter.annotation.JsonIgnore;
import com.jsoniter.annotation.JsonUnwrapper;
import com.jsoniter.output.JsonStream;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TaskResult {
    private String uuid;
    @JsonIgnore
    private Date timestamp;
    private String resultData;
    private int status = 0;
    private int errorCode = 0;
    private String errorDescription;
    private int sendCode;
    private String sendRes;
    private String docNumber;

    public TaskResult(String uuid){
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @JsonIgnore
    public Date getTimestamp() {
        return timestamp;
    }
    @JsonIgnore
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getResultData() {
        return resultData;
    }
    public void setResultData(String resultData) {
        this.resultData = resultData;
    }

    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status = status;
    }

    public int getErrorCode() {
        return errorCode;
    }
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }
    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public int getSendCode() {
        return sendCode;
    }
    public void setSendCode(int sendCode) {
        this.sendCode = sendCode;
    }

    public String getSendRes() {
        return sendRes;
    }
    public void setSendRes(String sendRes) {
        this.sendRes = sendRes;
    }

    public String getDocNumber() {
        return docNumber;
    }
    public void setDocNumber(String docNumber) {
        this.docNumber = docNumber;
    }

    @JsonUnwrapper
    public void writeTimestamp(JsonStream stream) throws IOException {
        stream.writeObjectField("timestamp");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        try{
            stream.writeVal(df.format(timestamp));
        }catch(Exception err){
            stream.writeVal(df.format(new Date()));
        }
    }
}
