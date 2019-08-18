package ru.nsg.atol.webserver.workers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.atol.drivers10.fptr.Fptr;
import ru.atol.drivers10.fptr.IFptr;
import ru.nsg.atol.webserver.entety.BlockRecord;
import ru.nsg.atol.webserver.entety.Task;
import ru.nsg.atol.webserver.entety.TaskResult;
import ru.nsg.atol.webserver.database.DBProvider;
import ru.nsg.atol.webserver.utils.Settings;
import ru.nsg.atol.webserver.utils.Utils;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

public class DriverWorker extends Thread {
    private static Logger logger = LogManager.getLogger(DriverWorker.class);
    private IFptr fptr = new Fptr();
    private HashMap<Integer, Boolean> timeQuery = new HashMap<>();
    private LocalDateTime startDate;

    public DriverWorker(String name) {
        super();
        setName(name);
        startDate = LocalDateTime.now();

        for (int i = 0; i <=24; i++)
            timeQuery.put(i, false);
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

                //Проверка блокировки очереди
                BlockRecord blockState = DBProvider.db.getBlockDeviceState(getName());
                if (blockState != null && !blockState.getUuid().isEmpty()) {
                    logger.info("Task queue lock detected {}", blockState.getUuid());
                    fptr.setParam(IFptr.LIBFPTR_PARAM_FN_DATA_TYPE, (long) IFptr.LIBFPTR_FNDT_LAST_DOCUMENT);
                    if (fptr.fnQueryData() != 0) {
                        logger.warn("Failed to recover, continue to try...");
                        continue;
                    }
                    boolean closed = fptr.getParamInt(IFptr.LIBFPTR_PARAM_DOCUMENT_NUMBER) > blockState.getDocumentNumber();
                    fptr.continuePrint();
                    logger.info("Connection restored, task {} {}", blockState.getUuid(), closed ? "done" : "not done");

                    Task task = DBProvider.db.getTask(blockState.getUuid());
                    TaskResult status = DBProvider.db.getTaskResult(blockState.getUuid());
                    if (task == null || status == null)
                        continue;

                    if (status.getStatus() == IFptr.LIBFPTR_FNDT_LAST_DOCUMENT) {
                        status.setStatus(closed ? 2 : 3);
                        if (closed) {
                            status.setErrorCode(IFptr.LIBFPTR_OK);
                            status.setErrorDescription("Ошибок нет");

                            boolean isReceipt = Utils.isReceipt(task.getDataJson().get("type").toString());
                            String getLastFiscalParams = "{\n\"type\": \"getLastFiscalParams\",\n\"forReceipt\": " + isReceipt + "\n}";
                            this.fptr.setParam(IFptr.LIBFPTR_PARAM_JSON_DATA, getLastFiscalParams);
                            if (this.fptr.processJson() == 0)
                                status.setResultData(this.fptr.getParamString(IFptr.LIBFPTR_PARAM_JSON_DATA));
                        }
                        if(DBProvider.db.updateTaskResult(status))
                            continue;
                        break;
                    }

                    logger.info("Task processing {} is completed, unlock the queue at {}", blockState.getUuid(), getName());
                    DBProvider.db.setTaskReady(blockState.getUuid());
                    DBProvider.db.unblockDBDevice(getName());
                }

                //Отработка очереди
                Task task = DBProvider.db.getNextTask(getName());
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
                                if (isNeedBlock(fptr.errorCode()) && lastDocumentNumber != -1L) {
                                    DBProvider.db.blockDBDevice(new BlockRecord(task.getUuid(), lastDocumentNumber, getName()));
                                    taskResult.setStatus(5);
                                } else {
                                    taskResult.setStatus(3);
                                }
                            }
                            taskResult.setErrorCode(fptr.errorCode());
                            taskResult.setErrorDescription(fptr.errorDescription());
                        }

                        switch(taskResult.getStatus()) {
                            case 2:
                                logger.info("task #{} execute without error", taskResult.getUuid());
                                break;
                            case 3:
                                logger.info("task #{} finished with error {}", taskResult.getUuid(), taskResult.getStatus());
                                break;
                            case 4:
                                logger.info("task #{} aborted due to previous errors", taskResult.getUuid());
                                break;
                            case 5:
                                logger.info("task #{} blocked queue", taskResult.getUuid());
                        }

                        DBProvider.db.updateTaskResult(taskResult);
                        if (taskResult.getStatus() != 5)
                            DBProvider.db.setTaskReady(task.getUuid());
                    }catch (Exception ex){
                        logger.error(getName(), ex);
                    }
                }
            }
        }
    }

    private boolean isNeedBlock(int error) {
        switch(error) {
            case 2:
            case 3:
            case 4:
                return true;
            case 15:
                return true;
            case 115:
            case 116:
            case 117:
            case 118:
            case 119:
            case 120:
            case 121:
            case 122:
            case 124:
            case 133:
            case 134:
            case 135:
            case 136:
            case 137:
            case 138:
            case 141:
            case 142:
            case 159:
                return true;
            default:
                return false;
        }
    }

}
