package ru.nsg.atol.webserver.serverlets;

import com.jsoniter.output.JsonStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsg.atol.webserver.entety.Task;
import ru.nsg.atol.webserver.entety.TaskResult;
import ru.nsg.atol.webserver.database.DBProvider;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class TaskResultServlet extends HttpServlet {
    private static final Logger logger = LogManager.getLogger(TaskResultServlet.class);

    public TaskResultServlet() {}

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(405);
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        TaskResult taskResult;
        try {
            String uuid = req.getPathInfo().split("/")[1];
            taskResult = DBProvider.db.getTaskResult(uuid);
        } catch (ArrayIndexOutOfBoundsException | NullPointerException arEx) {
            logger.error(arEx.getMessage(), arEx);
            resp.sendError(404, "No UUID");
            return;
        }

        if (taskResult == null) {
            resp.sendError(404);
        } else {
            logger.info(String.format("%s %s", req.getMethod(), req.getRequestURI()));
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(JsonStream.serialize(taskResult));
            logger.info("{} {}", resp.getStatus(), JsonStream.serialize(taskResult));
        }
    }

    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Task task;
        TaskResult taskResult;
        String uuid;
        try {
            uuid = req.getPathInfo().split("/")[1];
            task = DBProvider.db.getTask(uuid);
            taskResult = DBProvider.db.getTaskResult(uuid);
        } catch (ArrayIndexOutOfBoundsException var13) {
            logger.error(var13.getMessage(), var13);
            resp.sendError(404, "No UUID");
            return;
        }

        if (taskResult != null && task != null) {
            logger.info("{} {}", req.getMethod(), req.getRequestURI());
            if (task.isReady()) {
                resp.sendError(405, "Task done or canceled");
            } else {
                if (taskResult.getStatus() != 0) {
                    resp.sendError(405, "Task in progress");
                    return;
                }
                DBProvider.db.cancelTask(task.getUuid());
                taskResult.setStatus(6);
                List<TaskResult> taskResultList = new LinkedList<>();
                taskResultList.add(taskResult);
                DBProvider.db.updateTaskResultsStatus(taskResultList);
                resp.setStatus(200);
            }
        } else {
            resp.sendError(404);
        }
    }


}
