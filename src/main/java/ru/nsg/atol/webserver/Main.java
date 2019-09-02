package ru.nsg.atol.webserver;

import com.jsoniter.any.Any;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import ru.atol.drivers10.fptr.Fptr;
import ru.nsg.atol.webserver.database.DBProvider;
import ru.nsg.atol.webserver.serverlets.TaskRecipientServlet;
import ru.nsg.atol.webserver.serverlets.TaskResultServlet;
import ru.nsg.atol.webserver.utils.Jetty2Log4j2Bridge;
import ru.nsg.atol.webserver.utils.Settings;
import ru.nsg.atol.webserver.workers.DatabaseTaskSaver;
import ru.nsg.atol.webserver.workers.DriverWorker;
import ru.nsg.atol.webserver.workers.SenderWorker;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static Logger logger = LogManager.getLogger(Main.class);
    private static Server server;
    private static DatabaseTaskSaver databaseTaskSaver;
    private static HashMap<String, DriverWorker> driverMap = new HashMap();
    private static SenderWorker senderWorker;

    public static void main(String[] args) {
        //Logger
        Jetty2Log4j2Bridge jetty2Log4j2Bridge = new Jetty2Log4j2Bridge(Main.class.getName());
        org.eclipse.jetty.util.log.Log.setLog(jetty2Log4j2Bridge);
        //settings
        String jarPath = Paths.get("").toAbsolutePath().toString();
        File settings = new File(jarPath + File.separator +"conf" + File.separator + "settings.json");
        if (!settings.exists()) {
            try {
                settings.getParentFile().mkdirs();
                if (settings.createNewFile()){
                    Files.copy(Main.class.getResourceAsStream("/settings.json"), settings.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }catch (IOException ioEx){
                logger.error("Can't create " + settings.getAbsolutePath(), ioEx);
                System.exit(0);
            }
        }
        System.setProperty("settings.file", settings.toPath().toString());
        createTray();
        File fptr10_dll = new File("c:\\Program Files\\ATOL\\Drivers10\\KKT\\bin\\fptr10.dll");
        if (!fptr10_dll.exists()) {
            try {
                fptr10_dll.getParentFile().mkdirs();
                if (fptr10_dll.createNewFile()){
                    Files.copy(Main.class.getResourceAsStream("/fptr10.dll"), fptr10_dll.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }catch (IOException ioEx){
                logger.error("Can't create c:\\Program Files\\ATOL\\Drivers10\\KKT\\bin\\fptr10.dll from jar", ioEx);
                System.exit(0);
            }
        }

        if(args.length == 0 || "start".equals(args[0])){
            try {
                start(args);
            }catch (Exception exStart){logger.error("Exception server start: ", exStart);}
            try {
                stop(args);
            }catch (Exception exStop){logger.error("Exception server stop: ", exStop);}
        } else if ("stop".equals(args[0])) {
            try {
                stop(args);
            }catch (Exception exStop){logger.error("Exception server stop: ", exStop);}
        }
    }

    public static void start(String[] args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stop(null);
            } catch (Exception ex) {
                logger.error(ex);
            }
        }));

        logger.info("Start server for driver v. {}", new Fptr().version());

        logger.info("DataBase initialisation");
        DBProvider.db.init();

        databaseTaskSaver = new DatabaseTaskSaver();
        databaseTaskSaver.setName("databaseTaskSaver_" + databaseTaskSaver.getId());
        databaseTaskSaver.start();

        logger.info("Server start");
        server = new Server();

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(Settings.getServerPort());
        server.addConnector(connector);

        Configuration.ClassList classList = Configuration.ClassList.setServerDefault(server);
        classList.addBefore("org.eclipse.jetty.webapp.JettyWebXmlConfiguration", new String[]{"org.eclipse.jetty.annotations.AnnotationConfiguration"});
        FilterHolder filterHolder = new FilterHolder(CrossOriginFilter.class);
        filterHolder.setInitParameter("allowedOrigins", "*");
        filterHolder.setInitParameter("allowedMethods", "GET,POST,PUT,DELETE,HEAD,OPTIONS");
        ServletContextHandler servletContextHandler = new ServletContextHandler(1);
        servletContextHandler.setContextPath("/");

        servletContextHandler.addServlet(TaskRecipientServlet.class, "/requests");
        servletContextHandler.addServlet(TaskResultServlet.class, "/taskResult/*");

        servletContextHandler.addFilter(filterHolder, "/*", null);
        ServletHolder holderDefault = new ServletHolder("default", DefaultServlet.class);
        holderDefault.setInitParameter("dirAllowed", "false");
        servletContextHandler.addServlet(holderDefault, "/");

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{servletContextHandler});
        server.setHandler(handlers);
        server.start();

        if(!Settings.getResultsSendUri().isEmpty()){
            senderWorker = new SenderWorker();
            senderWorker.start();
            logger.info("resSender started");
        }
        Settings.getDevicesList().keySet().stream().forEach(key -> {
            DriverWorker driverWorker = new DriverWorker(key, senderWorker);
            driverMap.put(key, driverWorker);
            driverWorker.start();
            logger.info("driverWorker for {} {}", key, " started");
        });
        server.join();
    }

    public static synchronized void stop(String[] args) throws Exception {
        if (server != null && server.isRunning()) {
            logger.info("Server will be stopped...");
            for(int h = 0; h < server.getHandlers().length; ++h)
                server.getHandlers()[h].stop();

            server.stop();
            server.getThreadPool().join();
            logger.info("Server stopped");
        }

        driverMap.forEach((s, driverWorkerUnit) -> {
            if (driverWorkerUnit != null && !driverWorkerUnit.isInterrupted()) {
                driverWorkerUnit.interrupt();
                try {
                    driverWorkerUnit.join(100);
                } catch (InterruptedException e) {
                    logger.error("error " + driverWorkerUnit.getName(), e);
                    driverWorkerUnit.stop();
                }
                logger.info("{} stopped", driverWorkerUnit.getName());
            }
        });
        driverMap.clear();

        if (senderWorker != null && !senderWorker.isInterrupted()) {
            senderWorker.interrupt();
            try {
                senderWorker.join(5000);
            } catch (InterruptedException e) {
                logger.error("error " + senderWorker.getName(), e);
                senderWorker.stop();
            }
            logger.info("senderWorker stopped");
        }

        if (databaseTaskSaver != null && !databaseTaskSaver.isInterrupted()) {
            while (databaseTaskSaver.getQueueSize() > 0){
                logger.info("task on save in queue {}", databaseTaskSaver.getQueueSize());
                try {
                    databaseTaskSaver.join(2000);
                }catch (InterruptedException ex){}
            }
            databaseTaskSaver.interrupt();
            logger.info("databaseTaskSaver stopped");
        }
    }

    public static boolean addToQueueOnDatabaseSave(Any any) throws InterruptedException {
        return databaseTaskSaver.offer(any);
    }

    private static void createTray(){
        if (GraphicsEnvironment.isHeadless()) {
            logger.info("SystemTray is not supported");
            return;
        }
        if (!SystemTray.isSupported()) {
            logger.info("SystemTray is not supported");
            return;
        }
        final PopupMenu popup = new PopupMenu();

        MenuItem item00 = new MenuItem("Open settings");
        item00.addActionListener(e -> {
            try {
                URI uriSet = new URI(System.getProperty("settings.file"));
                File settingsFile = new File(uriSet);
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    String cmd = "rundll32 url.dll,FileProtocolHandler " + settingsFile.getCanonicalPath();
                    Runtime.getRuntime().exec(cmd);
                }
                else {
                    Desktop.getDesktop().edit(settingsFile);
                }
            } catch (URISyntaxException | IOException openEx) {
                logger.error("can't open file", openEx);
            }
        });
        popup.add(item00);

        MenuItem item0 = new MenuItem("Restart");
        item0.addActionListener(e -> {
            try {
                stop(null);
                start(null);
            }catch (Exception ex){
                logger.error(ex);
            }

            System.exit(0);
        });
        popup.add(item0);

        MenuItem item1 = new MenuItem("Exit");
        item1.addActionListener(e -> {
            /*try {
                stop(null);
            }catch (Exception ex){
                logger.error(ex);
            }*/
            System.exit(0);
        });
        popup.add(item1);

        final TrayIcon trayIcon;
        try {
            trayIcon = new TrayIcon(ImageIO.read(Main.class.getResourceAsStream("/tray.png")));
            final SystemTray tray = SystemTray.getSystemTray();
            tray.add(trayIcon);
            trayIcon.setPopupMenu(popup);
        } catch (IOException | AWTException e) {
            logger.warn("TrayIcon could not be added.");
        }
    }

    public static DriverWorker getDriverWorkerByDevise(String device){
        return driverMap.get(device);
    }
}
