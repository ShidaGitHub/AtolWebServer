package ru.nsg.atol.webserver.utils;

import ru.nsg.atol.webserver.entety.TaskResult;

public interface TaskCompleteListener {
    void onTaskReadyToSend(TaskResult taskResult);
}
