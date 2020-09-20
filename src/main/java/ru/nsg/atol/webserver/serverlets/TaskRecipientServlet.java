package ru.nsg.atol.webserver.serverlets;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import com.jsoniter.output.JsonStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsg.atol.webserver.Main;
import ru.nsg.atol.webserver.entety.DeviceStatus;
import ru.nsg.atol.webserver.utils.Settings;
import ru.nsg.atol.webserver.utils.Utils;
import ru.nsg.atol.webserver.workers.DriverWorker;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedList;

public class TaskRecipientServlet extends HttpServlet {
    private static final Logger logger = LogManager.getLogger(TaskRecipientServlet.class);

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String devKeyName = req.getParameter("device") == null ? req.getParameter("unit") : req.getParameter("device");
        if (devKeyName == null) {
            resp.sendError(400, "parameter \"device(or unit)\" not found");
            return;
        }
        if (!Settings.getDevicesList().keySet().contains(devKeyName) && !devKeyName.equals("all")){
            resp.sendError(404, "device '" + devKeyName + "' not found");
            return;
        }

        if (devKeyName.equals("all")){
            LinkedList<DeviceStatus> res = new LinkedList<>();
            for (String dev : Settings.getDevicesList().keySet()){
                DriverWorker dw = Main.getDriverWorkerByDevise(dev);
                res.add(new DeviceStatus(dev, dw.isDeviceBlocked(), dw.isPrinterOverheat()));
            }
            resp.setContentType("application/json");
            resp.getWriter().write(JsonStream.serialize(res));
        }else {
            resp.setContentType("application/json");
            DriverWorker dw = Main.getDriverWorkerByDevise(devKeyName);
            resp.getWriter().write(JsonStream.serialize(new DeviceStatus(dw.getName(), dw.isDeviceBlocked(), dw.isPrinterOverheat())));
        }
        resp.getWriter().flush();
        resp.getWriter().close();

        resp.setStatus(HttpServletResponse.SC_OK);
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Any any = JsonIterator.deserialize(Utils.readInputStream(req.getInputStream()));
        String devKeyName = any.get("device").toString().isEmpty() ? "unit" : "device";

        if (!any.keys().contains("uuid") || !any.keys().contains(devKeyName) || !any.keys().contains("request")) {
            resp.sendError(400, "\"uuid\" and / or \"request\" and / or \"device(or unit)\" not found");
            return;
        }
        if (!Settings.getDevicesList().containsKey(any.get(devKeyName).toString())){
            resp.sendError(404, "No \"device\" " + any.get(devKeyName));
            return;
        }
        try {
            Main.addToQueueOnDatabaseSave(any);
        } catch (InterruptedException ex) {
            logger.error(ex);
            resp.sendError(400, ex.getMessage());
        }
        resp.setStatus(201);
    }
}
