package ru.nsg.atol.webserver.utils;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import com.jsoniter.spi.JsonException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Settings {
    private static final Logger logger = LogManager.getLogger(Settings.class);
    private static Any jsonSettings;

    private Settings() {}

    protected static Any getJsonObject() throws IOException{
        if (jsonSettings == null){
            jsonSettings = JsonIterator.deserialize(Files.lines(Paths.get(System.getProperty("settings.file"))).collect(Collectors.joining()));
        }
        return jsonSettings;
    }

    public static int getServerPort(){
        try {
            return getJsonObject().get("web").get("port").toInt();
        } catch (JsonException | IOException ex) {
            logger.error("Can't read settings from " + System.getProperty("settings.file"), ex);
            return 0;
        }
    }

    public static Map<String, Any> getDevicesList(){
        try {
            return getJsonObject().get("devices").asMap();
        } catch (JsonException | IOException ex) {
            logger.error("Can't read settings from " + System.getProperty("settings.file"), ex);
            return new HashMap<>();
        }
    }

    public static String getDriverSettings(String deviceName){
        if (getDevicesList().containsKey(deviceName))
            return getDevicesList().get(deviceName).toString();
        else
            return null;
    }

    public static String getResultsSendUri(){
        try {
            return getJsonObject().get("resultsSend").get("uri").toString();
        } catch (JsonException | IOException ex) {
            logger.error("Can't read settings from " + System.getProperty("settings.file"), ex);
            return null;
        }
    }

    public static String getResultsSendLogin(){
        try {
            return getJsonObject().get("resultsSend").get("login").toString();
        } catch (JsonException | IOException ex) {
            logger.error("Can't read settings from " + System.getProperty("settings.file"), ex);
            return "login";
        }
    }

    public static String getResultsSendPass(){
        try {
            return getJsonObject().get("resultsSend").get("password").toString();
        } catch (JsonException | IOException ex) {
            logger.error("Can't read settings from " + System.getProperty("settings.file"), ex);
            return "password";
        }
    }
}
