package ru.nsg.atol.webserver.workers;

import com.jsoniter.output.JsonStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import ru.nsg.atol.webserver.database.DBProvider;
import ru.nsg.atol.webserver.entety.TaskResult;
import ru.nsg.atol.webserver.utils.Settings;
import ru.nsg.atol.webserver.utils.TaskCompleteListener;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class SenderWorker extends Thread implements TaskCompleteListener {
    private static final Logger logger = LogManager.getLogger(SenderWorker.class);
    private final BlockingQueue<TaskResult> queueToSend;
    private LocalDateTime checkDate;

    public SenderWorker() {
        setName(this.getClass().getName());
        checkDate = LocalDateTime.now();
        queueToSend = new LinkedBlockingQueue(100000);
    }

    @Override
    public void run() {
        long sleepTimeout = 100;
        HttpClient httpClient = new HttpClient();
        httpClient.setConnectTimeout(3000);
        httpClient.setIdleTimeout(900000);
        try {
            httpClient.start();
        }catch (Exception startEx){
            logger.error("Can't create HttpClient", startEx);
            interrupt();
        }

        while(!this.isInterrupted()) {
            try {
                Thread.sleep(sleepTimeout);
            } catch (InterruptedException var18) {
                continue;
            }

            //Каждые 10 мин проверить базу и при необхоимостии пополнить очередь отправки из базы.
            if (LocalDateTime.now().getMinute() % 5 == 0 && checkDate.getMinute() != LocalDateTime.now().getMinute()){
                checkDate = LocalDateTime.now();
                List<TaskResult> resultList = DBProvider.db.getResultTasksToSend();
                if (resultList != null){
                    resultList.stream().forEach(taskResult -> queueToSend.offer(taskResult));
                }
            }

            List<TaskResult> resultList = new LinkedList<>();
            while (!queueToSend.isEmpty() && resultList.size() <= 500){
                resultList.add(queueToSend.poll());
            }

            if (resultList != null && !resultList.isEmpty()){
                try {
                    String login = Settings.getResultsSendLogin();
                    String password = Settings.getResultsSendPass();

                    if (!httpClient.isRunning())
                        httpClient.start();

                    Request request = httpClient.newRequest(Settings.getResultsSendUri());
                    request.header(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    request.header(HttpHeader.ACCEPT_ENCODING, "gzip, deflate");
                    request.header(HttpHeader.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((login + ":" + password).getBytes(Charset.forName("UTF-8"))));
                    request.header(HttpHeader.CONNECTION, "keep-alive");
                    request.header(HttpHeader.CACHE_CONTROL, "max-age=0, no-cache");
                    request.header(HttpHeader.PRAGMA, "no-cache");
                    request.method(HttpMethod.POST);

                    request.content(new StringContentProvider(JsonStream.serialize(resultList), Charset.forName("UTF-8")));
                    ContentResponse contentResponse = request.send();

                    resultList.forEach(unit -> {
                        unit.setSendCode(contentResponse.getStatus());
                        unit.setSendRes(contentResponse.getContentAsString());
                    });
                    DBProvider.db.updateTaskResultsStatus(resultList);
                    if (contentResponse.getStatus() != 200)
                        throw new ExecutionException(new RuntimeException(contentResponse.getContentAsString()));

                    logger.info("sended {}", resultList.size());
                    sleepTimeout = 100;
                } catch (InterruptedException | ExecutionException | TimeoutException ex0) {
                    logger.error("Can't send data " + (resultList.size() > 0 ?
                            resultList.stream().map(taskResult -> taskResult.getUuid()).collect(Collectors.joining(", ", "{", "}")) :
                            ""), ex0);
                    sleepTimeout = 1000;
                } catch (Exception ex2) {
                    logger.error("Can't connect, SenderWorker. Sleep 60 sec", ex2);
                    sleepTimeout = 60000;
                }
            }
        }

        if (!httpClient.isRunning()) {
            try {
                httpClient.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onTaskReadyToSend(TaskResult taskResult) {
        queueToSend.offer(taskResult);
    }
}

