package ru.nsg.atol.webserver.database;

import ru.nsg.atol.webserver.entety.BlockRecord;
import ru.nsg.atol.webserver.entety.Task;
import ru.nsg.atol.webserver.entety.TaskResult;
import java.util.List;

public interface DatabaseDAO {
    boolean init();
    boolean addTaskList(List<Task> list);
    boolean addTaskResultList(List<TaskResult> list);
    BlockRecord getBlockDeviceState(String name);
    TaskResult getTaskResult(String uuid);
    Task getTask(String uuid);
    boolean updateTaskResult(TaskResult taskResult);
    boolean setTaskReady(String uuid);
    Task getNextTask(String device);
    boolean cancelTask(String uuid);
    boolean blockDBDevice(BlockRecord blockRecord);
    boolean unblockDBDevice(String device);
    List<TaskResult> getResultTasksToSend();
    boolean updateTaskResultsStatus(List<TaskResult> taskResultList);
}
