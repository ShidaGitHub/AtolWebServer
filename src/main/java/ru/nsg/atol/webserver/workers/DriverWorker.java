package ru.nsg.atol.webserver.workers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.atol.drivers10.fptr.Fptr;
import ru.atol.drivers10.fptr.IFptr;
import ru.nsg.atol.webserver.database.DBProvider;
import ru.nsg.atol.webserver.entety.Task;
import ru.nsg.atol.webserver.entety.TaskResult;
import ru.nsg.atol.webserver.utils.Settings;
import ru.nsg.atol.webserver.utils.TaskCompleteListener;
import ru.nsg.atol.webserver.utils.Utils;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

public class DriverWorker extends Thread {
    private static final Logger logger = LogManager.getLogger(DriverWorker.class);
    private final BlockingQueue<Task> queueToFptr;
    private final IFptr fptr = new Fptr();
    private final HashMap<Integer, Boolean> timeQuery = new HashMap<>();
    private final LocalDateTime startDate;
    private LocalDateTime checkNotReadyTaskDate;
    private LocalDateTime checkStatusDeviceDate;
    private final int checkStatusDeviceDelay;
    private final TaskCompleteListener taskCompleteListener;

    private boolean isDeviceBlocked, isPrinterOverheat;

    public DriverWorker(String name, TaskCompleteListener taskCompleteListener) {
        super();
        setName(name);
        this.taskCompleteListener = taskCompleteListener;
        queueToFptr = new LinkedBlockingQueue(100000);
        startDate = LocalDateTime.now();
        checkNotReadyTaskDate = LocalDateTime.now();
        IntStream.rangeClosed(0, 24).forEachOrdered(i -> timeQuery.put(i, false));
        checkStatusDeviceDelay = Settings.getDeviceCheckerDelay();
        checkStatusDeviceDate = LocalDateTime.now();
        isDeviceBlocked = true;
    }

    public void offer(Task task) {
        if (!queueToFptr.contains(task))
            queueToFptr.offer(task);
    }

    public void run() {
        boolean opened = false;
        long sleepTimeout = 200;

        while(!this.isInterrupted()) {
            try {
                Thread.sleep(sleepTimeout);
            } catch (InterruptedException entEx) {
                continue;
            }

            if (!opened) {
                try {
                    fptr.setSettings(Settings.getDriverSettings(getName()));
                } catch (Exception setEx) {
                    logger.error(getName(), setEx);
                    return;
                }
                fptr.open();
                opened = fptr.isOpened();
            }

            if (!opened) {
                sleepTimeout = 5000;
                if(ChronoUnit.SECONDS.between(startDate, LocalDateTime.now()) > 60 * 5) { //5 минут на первоначальную загрузку
                    //Раз в час отослать предупреждение
                    if (!timeQuery.get(LocalTime.now().getHour())) {
                        IntStream.rangeClosed(0, 24).forEachOrdered(h -> timeQuery.put(h, false));
                        timeQuery.put(LocalTime.now().getHour(), true);

                        TaskResult driverChecker = new TaskResult(UUID.randomUUID().toString());
                        driverChecker.setTimestamp(new Date());
                        driverChecker.setStatus(2);
                        driverChecker.setErrorCode(431);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                        driverChecker.setErrorDescription("Поток " + getName() + " касса не отвечает - " + dateFormat.format(new Date()));
                        driverChecker.setResultData("");
                        driverChecker.setDocNumber("-1");
                        driverChecker.setSendCode(0);
                        driverChecker.setSendRes("");

                        LinkedList<TaskResult> taskResList = new LinkedList<>();
                        taskResList.add(driverChecker);
                        if(!DBProvider.db.addTaskResultList(taskResList))
                            continue;

                        logger.error(getName() + " no connect - {}", dateFormat.format(new Date()));
                    }
                }
            } else {
                sleepTimeout = 200;

                //Каждые 10 мин проверить базу и при необхоимостии пополнить очередь обработки из базы.
                if (LocalDateTime.now().getMinute() % 10 == 0 && checkNotReadyTaskDate.getMinute() != LocalDateTime.now().getMinute()){
                    checkNotReadyTaskDate = LocalDateTime.now();
                    List<Task> notReadyTask = DBProvider.db.getNotReadyTasks(getName());
                    if (notReadyTask != null){
                        notReadyTask.stream().forEach(task -> offer(task));
                    }
                }

                //Каждые checkStatusDeviceDelay мин проверить состояние кассы.
                if (LocalDateTime.now().getMinute() % checkStatusDeviceDelay == 0 && checkStatusDeviceDate.getMinute() != LocalDateTime.now().getMinute()){
                    checkStatusDeviceDate = LocalDateTime.now();
                    try {
                        fptr.setParam(IFptr.LIBFPTR_PARAM_DATA_TYPE, IFptr.LIBFPTR_DT_STATUS);
                        fptr.queryData();
                        isDeviceBlocked = fptr.getParamBool(IFptr.LIBFPTR_PARAM_BLOCKED);
                        isPrinterOverheat = fptr.getParamBool(IFptr.LIBFPTR_PARAM_PRINTER_OVERHEAT);
                    }catch (Exception ex){
                        isDeviceBlocked = true;
                        logger.error(getName(), ex);
                    }
                }

                //Обработка очереди
                while(!queueToFptr.isEmpty()) {
                    Task task = queueToFptr.poll();
                    if (task != null) {
                        logger.info("find task, uuid {} in {}", task.getUuid(), task.getDevice());

                        try {
                            TaskResult taskResult = new TaskResult(task.getUuid());
                            taskResult.setStatus(1);
                            taskResult.setDocNumber(task.getDocNumber());
                            DBProvider.db.updateTaskResult(taskResult);

                            long lastDocumentNumber = -1L;
                            if (task.getDataJson().keys().contains("type") && Utils.isFiscalOperation(task.getDataJson().get("type").toString())){
                                fptr.setParam(IFptr.LIBFPTR_PARAM_FN_DATA_TYPE, 5L);
                                if (fptr.fnQueryData() < 0) {
                                    taskResult.setStatus(3);
                                    taskResult.setErrorCode(fptr.errorCode());
                                    taskResult.setErrorDescription(fptr.errorDescription());
                                    logger.info(taskResult.getErrorDescription());
                                }
                                lastDocumentNumber = fptr.getParamInt(IFptr.LIBFPTR_PARAM_DOCUMENT_NUMBER);
                            }

                            if (taskResult.getStatus() != 3) {
                                fptr.setParam(IFptr.LIBFPTR_PARAM_JSON_DATA, task.getDataJson().toString());
                                if (fptr.processJson() >= 0) {
                                    taskResult.setStatus(2);
                                    taskResult.setResultData(fptr.getParamString(IFptr.LIBFPTR_PARAM_JSON_DATA));
                                } else {
                                    taskResult.setStatus(3);
                                }
                                taskResult.setErrorCode(fptr.errorCode());
                                taskResult.setErrorDescription(fptr.errorDescription());
                            }

                            switch(taskResult.getStatus()) {
                                case 2:
                                    logger.info("task #{} in {} execute without error", taskResult.getUuid(), getName());
                                    break;
                                case 3:
                                    logger.info("task #{} in {} finished with error {}", taskResult.getUuid(), getName(), taskResult.getStatus());
                                    break;
                                case 4:
                                    logger.info("task #{} in {} aborted due to previous errors", taskResult.getUuid(), getName());
                                    break;
                                case 5:
                                    logger.info("task #{} in {} blocked queue", taskResult.getUuid(), getName());
                            }

                            DBProvider.db.updateTaskResult(taskResult);
                            if (taskResult.getStatus() != 5){
                                DBProvider.db.setTaskReady(task.getUuid());
                            }
                            taskCompleteListener.onTaskReadyToSend(taskResult);
                        }catch (Exception ex){
                            logger.error(getName(), ex);
                        }
                    }
                }
            }
        }
    }

    public boolean isPrinterOverheat() {
        return isPrinterOverheat;
    }

    public boolean isDeviceBlocked() {
        return isDeviceBlocked;
    }
}
