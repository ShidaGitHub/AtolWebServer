package ru.nsg.atol.webserver.serverlets;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsg.atol.webserver.Main;
import ru.nsg.atol.webserver.utils.Settings;
import ru.nsg.atol.webserver.utils.Utils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TaskRecipientServlet extends HttpServlet {
    private static final Logger logger = LogManager.getLogger(TaskRecipientServlet.class);

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendError(405);
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
