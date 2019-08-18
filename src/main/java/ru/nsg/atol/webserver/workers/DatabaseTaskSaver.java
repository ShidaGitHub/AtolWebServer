package ru.nsg.atol.webserver.workers;

import com.jsoniter.any.Any;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.nsg.atol.webserver.entety.Task;
import ru.nsg.atol.webserver.database.DBProvider;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DatabaseTaskSaver extends Thread {
    private static Logger logger = LogManager.getLogger(DatabaseTaskSaver.class);
    private static BlockingQueue<Any> queueToBase;

    public DatabaseTaskSaver(){
        queueToBase = new LinkedBlockingQueue(100000);
    }

    public boolean offer(Any any) throws InterruptedException {
        if (this.isInterrupted()) return false;
        return queueToBase.offer(any, 1, TimeUnit.SECONDS);
    }

    public int getQueueSize(){
        return queueToBase.size();
    }

    @Override
    public void run() {
        long sleepTimeout = 1000;

        while(!this.isInterrupted() || !queueToBase.isEmpty()) {
            try {
                Thread.sleep(sleepTimeout);
            } catch (InterruptedException ex) {
                continue;
            }
            LinkedList<Task> portion = new LinkedList<>();
            while (!queueToBase.isEmpty()){
                try {
                    portion.add(new Task(queueToBase.poll()));
                }catch (Exception ex){
                    ex.printStackTrace();
                    logger.error(ex);
                    continue;
                }

                if (portion.size() == 500){
                    DBProvider.db.addTaskList(portion);
                    portion.clear();
                }
            }
            if (!portion.isEmpty()) {
                DBProvider.db.addTaskList(portion);
            }
        }
    }
}
