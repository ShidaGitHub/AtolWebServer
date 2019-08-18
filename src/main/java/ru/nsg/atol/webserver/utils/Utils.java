package ru.nsg.atol.webserver.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class Utils {
    private static Logger logger = LogManager.getLogger(Utils.class);
    private Utils() {
    }

    public static String readInputStream(InputStream inputStream) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8.name()))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            logger.error(e);
            return "";
        }
    }

    public static String getStatusString(int status) {
        switch(status) {
            case 0:
                return "wait";
            case 1:
                return "inProgress";
            case 2:
                return "ready";
            case 3:
                return "error";
            case 4:
                return "interrupted";
            case 5:
                return "blocked";
            case 6:
                return "canceled";
            default:
                return "unknown";
        }
    }

    public static boolean isFiscalOperation(String operationType) {
        byte var2 = -1;
        switch(operationType.hashCode()) {
            case -2093190230:
                if (operationType.equals("closeArchive")) {
                    var2 = 12;
                }
                break;
            case -2072995478:
                if (operationType.equals("closeShift")) {
                    var2 = 1;
                }
                break;
            case -1763617128:
                if (operationType.equals("fnChange")) {
                    var2 = 10;
                }
                break;
            case -1735815341:
                if (operationType.equals("changeRegistrationParameters")) {
                    var2 = 11;
                }
                break;
            case -1350309703:
                if (operationType.equals("registration")) {
                    var2 = 9;
                }
                break;
            case -828298410:
                if (operationType.equals("buyReturn")) {
                    var2 = 5;
                }
                break;
            case -299529596:
                if (operationType.equals("buyCorrection")) {
                    var2 = 7;
                }
                break;
            case -237980894:
                if (operationType.equals("sellReturn")) {
                    var2 = 4;
                }
                break;
            case 97926:
                if (operationType.equals("buy")) {
                    var2 = 3;
                }
                break;
            case 3526482:
                if (operationType.equals("sell")) {
                    var2 = 2;
                }
                break;
            case 1037325294:
                if (operationType.equals("reportOfdExchangeStatus")) {
                    var2 = 8;
                }
                break;
            case 1534348368:
                if (operationType.equals("sellCorrection")) {
                    var2 = 6;
                }
                break;
            case 1534899288:
                if (operationType.equals("openShift")) {
                    var2 = 0;
                }
        }

        switch(var2) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
                return true;
            default:
                return false;
        }
    }

    public static boolean isReceipt(String operationType) {
        byte var2 = -1;
        switch(operationType.hashCode()) {
            case -828298410:
                if (operationType.equals("buyReturn")) {
                    var2 = 3;
                }
                break;
            case -299529596:
                if (operationType.equals("buyCorrection")) {
                    var2 = 5;
                }
                break;
            case -237980894:
                if (operationType.equals("sellReturn")) {
                    var2 = 2;
                }
                break;
            case 97926:
                if (operationType.equals("buy")) {
                    var2 = 1;
                }
                break;
            case 3526482:
                if (operationType.equals("sell")) {
                    var2 = 0;
                }
                break;
            case 1534348368:
                if (operationType.equals("sellCorrection")) {
                    var2 = 4;
                }
        }

        switch(var2) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return true;
            default:
                return false;
        }
    }
}