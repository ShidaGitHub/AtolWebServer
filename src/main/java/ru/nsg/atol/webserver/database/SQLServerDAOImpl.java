package ru.nsg.atol.webserver.database;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import ru.nsg.atol.webserver.entety.BlockRecord;
import ru.nsg.atol.webserver.entety.Task;
import ru.nsg.atol.webserver.entety.TaskResult;

import javax.persistence.PersistenceException;
import java.time.ZoneId;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class SQLServerDAOImpl implements DatabaseDAO {
    private static final Logger logger = LogManager.getLogger(SQLServerDAOImpl.class);

    @Override
    public boolean init(){
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            session.beginTransaction();
            session.createSQLQuery("IF OBJECT_ID(N'dbo.json_task', N'U') IS NULL " +
                    "create table json_task(" +
                    "uuid VARCHAR(100) constraint json_task_pk primary key nonclustered, " +
                    "device VARCHAR(100) not null, " +
                    "timestamp datetime, " +
                    "data VARCHAR(8000), " +
                    "docNumber VARCHAR(100), " +
                    "is_ready bit DEFAULT 0, " +
                    "is_canceled bit DEFAULT 0)").executeUpdate();

            session.createSQLQuery("IF NOT EXISTS (SELECT Name FROM sysindexes WHERE Name = 'json_task_device') " +
                    "CREATE NONCLUSTERED INDEX json_task_device ON json_task (device ASC) " +
                    "WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)").executeUpdate();

            session.createSQLQuery("IF NOT EXISTS (SELECT Name FROM sysindexes WHERE Name = 'json_task_docNumber') " +
                    "CREATE NONCLUSTERED INDEX json_task_docNumber ON json_task (docNumber ASC) " +
                    "WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)").executeUpdate();

            session.createSQLQuery("IF NOT EXISTS (SELECT Name FROM sysindexes WHERE Name = 'next_task') " +
                    "CREATE NONCLUSTERED INDEX next_task ON json_task (device ASC, is_ready ASC, is_canceled ASC) " +
                        "WHERE (json_task.is_ready <> (1) AND json_task.is_canceled <> (1)) " +
                    "WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)").executeUpdate();

            session.createSQLQuery("IF OBJECT_ID(N'dbo.json_results', N'U') IS NULL " +
                    "CREATE TABLE json_results (" +
                    "uuid VARCHAR (100) constraint json_results_pk primary key nonclustered, " +
                    "timestamp DATETIME, " +
                    "status INTEGER DEFAULT 0, " +
                    "error_code INTEGER DEFAULT 0, " +
                    "error_description VARCHAR(8000) DEFAULT '', " +
                    "result_data VARCHAR(8000) DEFAULT '', " +
                    "docNumber VARCHAR(100), " +
                    "send_code INTEGER DEFAULT 0, " +
                    "send_res TEXT DEFAULT '')").executeUpdate();

            session.createSQLQuery("IF NOT EXISTS (SELECT Name FROM sysindexes WHERE Name = 'json_results_docNumber') " +
                    "CREATE NONCLUSTERED INDEX json_results_docNumber on json_results (docNumber) " +
                    "WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)").executeUpdate();

            session.createSQLQuery("IF NOT EXISTS (SELECT Name FROM sysindexes WHERE Name = 'next_json_results') " +
                    "CREATE NONCLUSTERED INDEX next_json_results ON json_results (status ASC, send_code ASC) " +
                        "WHERE (send_code = (0) AND status > (1)) " +
                    "WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)").executeUpdate();

            session.createSQLQuery("IF OBJECT_ID(N'dbo.state_device', N'U') IS NULL " +
                    "CREATE TABLE state_device (" +
                    "state_id  VARCHAR (100) PRIMARY KEY NOT NULL, " +
                    "device VARCHAR(100) NOT NULL, " +
                    "value_state VARCHAR(3000))").executeUpdate();

            session.getTransaction().commit();
            return true;
        } catch (PersistenceException pe) {
            logger.error("Can't init database", pe);
            return false;
        }
    }

    @Override
    public synchronized boolean addTaskList(List<Task> list) {
        for (Task task : list) {
            try (Session session = HibernateUtility.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                Query query = session.createSQLQuery("INSERT INTO json_task(uuid, device, timestamp, data, docNumber) VALUES(:uuid, :device, :timestamp, :data, :docNumber)");
                query.setParameter("uuid", task.getUuid());
                query.setParameter("device", task.getDevice());
                query.setParameter("timestamp", task.getTimestamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                query.setParameter("data", task.getData());
                query.setParameter("docNumber", task.getDocNumber());
                query.executeUpdate();
                tx.commit();

                tx = session.beginTransaction();
                query = session.createSQLQuery("INSERT INTO json_results(uuid, timestamp, docNumber) VALUES(:uuid, :timestamp, :docNumber)");
                query.setParameter("uuid", task.getUuid());
                query.setParameter("timestamp", task.getTimestamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                query.setParameter("docNumber", task.getDocNumber());
                query.executeUpdate();
                tx.commit();
            } catch (Exception pe) {
                logger.error("Can't add TaskList " + task.getUuid(), pe);
            }
        }
        return true;
    }

    @Override
    public synchronized boolean addTaskResultList(List<TaskResult> list) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            int i = 0;
            for (TaskResult taskResult : list) {
                Query query = session.createSQLQuery("INSERT INTO json_results (uuid, timestamp, status, error_code, error_description, result_data, docNumber, send_code, send_res) " +
                                                        "VALUES (:uuid, :timestamp, :stat, :error_code, :error_description, :result_data, :docNumber, :send_code, :send_res)");
                query.setParameter("uuid", taskResult.getUuid());
                query.setParameter("timestamp", taskResult.getTimestamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                query.setParameter("stat", taskResult.getStatus());
                query.setParameter("error_code", taskResult.getErrorCode());
                query.setParameter("error_description", taskResult.getErrorDescription());
                query.setParameter("result_data", taskResult.getResultData());
                query.setParameter("docNumber", taskResult.getDocNumber());
                query.setParameter("send_code", taskResult.getSendCode());
                query.setParameter("send_res", taskResult.getSendRes());
                query.executeUpdate();
                if (i % 1000 == 0) {
                    session.flush();
                    session.clear();
                }
                i++;
            }
            tx.commit();
            return true;
        } catch (PersistenceException pe) {
            logger.error("Can't add ResultList", pe);
            return false;
        }
    }

    @Override
    public BlockRecord getBlockDeviceState(String device) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Query query = session.createSQLQuery("SELECT state_id, value_state FROM state_device WHERE device = :device");
            query.setParameter("device", device);
            Iterator iterator= query.list().iterator();

            String blockUUID = "";
            String blockDocument = "-1";

            while(iterator.hasNext()) {
                Object[] tuple = (Object[]) iterator.next();

                String key = String.valueOf(tuple[0]);
                String value = String.valueOf(tuple[1]);

                switch(key) {
                    case "block_last_fd":
                        if (key.equals("block_last_fd"))
                            blockDocument = value;
                        break;
                    case "block_uuid":
                        if (key.equals("block_uuid"))
                            blockUUID = value;
                }
            }
            session.close();
            return new BlockRecord(blockUUID, Long.parseLong(blockDocument), device);
        } catch (PersistenceException | NumberFormatException pe) {
            logger.error("Can't get BlockDeviceState", pe);
            return null;
        }
    }

    @Override
    public TaskResult getTaskResult(String uuid) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Query query = session.createSQLQuery("SELECT status, error_code, error_description, result_data, docNumber FROM json_results WHERE uuid = :uuid");
            query.setParameter("uuid", uuid);
            Iterator iterator= query.list().iterator();
            TaskResult taskResult = new TaskResult(uuid);
            if(iterator.hasNext()) {
                Object[] tuple = (Object[]) iterator.next();
                taskResult.setStatus((Integer) tuple[0]);
                taskResult.setErrorCode((Integer) tuple[1]);
                taskResult.setErrorDescription((String) tuple[2]);
                taskResult.setResultData((String) tuple[3]);
                taskResult.setDocNumber((String) tuple[4]);
            }
            session.close();
            return taskResult;
        } catch (PersistenceException pe) {
            logger.error("Can't get TaskResult", pe);
            return null;
        }
    }

    @Override
    public Task getTask(String uuid) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Query query = session.createSQLQuery("SELECT device, timestamp, data, docNumber, is_ready, is_canceled FROM json_task WHERE uuid = :uuid");
            query.setParameter("uuid", uuid);
            Iterator iterator= query.list().iterator();
            Task task = new Task(uuid);
            if(iterator.hasNext()) {
                Object[] tuple = (Object[]) iterator.next();
                task.setDevice((String) tuple[0]);
                task.setData((String) tuple[2]);
                task.setDocNumber((String) tuple[3]);
                task.setReady(((Integer) tuple[4]) > 0);
                task.setCanceled(((Integer) tuple[5]) > 0);
            }
            session.close();
            return task;
        } catch (PersistenceException pe) {
            logger.error("Can't get Task", pe);
            return null;
        }
    }

    @Override
    public synchronized boolean updateTaskResult(TaskResult taskResult) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Query query = session.createSQLQuery("UPDATE json_results SET status = :stat, error_code = :error_code, " +
                                                    "error_description = :error_description, result_data = :result_data, " +
                                                    "docNumber = :docNumber " +
                                                    "WHERE uuid = :uuid");

            query.setParameter("stat", taskResult.getStatus());
            query.setParameter("error_code", taskResult.getErrorCode());
            query.setParameter("error_description", taskResult.getErrorDescription());
            query.setParameter("result_data", taskResult.getResultData());
            query.setParameter("docNumber", taskResult.getDocNumber());
            query.setParameter("uuid", taskResult.getUuid());
            query.executeUpdate();
            tx.commit();
            return true;
        } catch (PersistenceException pe) {
            logger.error("Can't update json_results", pe);
            return false;
        }
    }

    @Override
    public synchronized boolean setTaskReady(String uuid) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Query query = session.createSQLQuery("UPDATE json_task SET  is_ready = 1 WHERE uuid = :uuid");
            query.setParameter("uuid", uuid);
            query.executeUpdate();
            tx.commit();
            return true;
        } catch (PersistenceException pe) {
            logger.error("Can't set task ready status", pe);
            return false;
        }
    }

    @Override
    public synchronized boolean blockDBDevice(BlockRecord blockRecord) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Query query = session.createSQLQuery("IF (SELECT COUNT(state_id) FROM state_device WHERE state_id = :state_id AND device = :device AND value_state = :value_state) = 0 " +
                                                        "INSERT INTO state_device (state_id, device, value_state) VALUES (:state_id, :device, :value_state)");
            query.setParameter("state_id", "block_uuid");
            query.setParameter("device", blockRecord.getDevice());
            query.setParameter("value_state", blockRecord.getUuid());
            query.executeUpdate();

            query = session.createSQLQuery("IF (SELECT COUNT(state_id) FROM state_device WHERE state_id = :state_id AND device = :device AND value_state = :value_state) = 0 " +
                                                "INSERT INTO state_device (state_id, device, value_state) VALUES (:state_id, :device, :value_state)");
            query.setParameter("state_id", "block_last_fd");
            query.setParameter("device", blockRecord.getDevice());
            query.setParameter("value_state", blockRecord.getDocumentNumber());
            query.executeUpdate();

            tx.commit();
            return true;
        } catch (PersistenceException pe) {
            logger.error("Can't block DBDevice", pe);
            return false;
        }
    }

    @Override
    public synchronized boolean unblockDBDevice(String device) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Query query = session.createSQLQuery("DELETE FROM state WHERE state_id = :state_id AND device = :device");
            query.setParameter("state_id", "block_uuid");
            query.setParameter("device", device);
            query.executeUpdate();

            query = session.createSQLQuery("DELETE FROM state WHERE state_id = :state_id AND device = :device");
            query.setParameter("state_id", "block_last_fd");
            query.setParameter("device", device);
            query.executeUpdate();
            tx.commit();
            return true;
        } catch (PersistenceException pe) {
            logger.error("Can't unblock DBDevice", pe);
            return false;
        }
    }

    @Override
    public Task getNextTask(String device) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Query query = session.createSQLQuery("SELECT uuid, data, timestamp, docNumber FROM json_task WHERE device = :device AND is_ready != 1 AND is_canceled != 1");
            query.setParameter("device", device);
            query.setMaxResults(1);

            Iterator iterator= query.list().iterator();
            if(iterator.hasNext()) {
                Object[] tuple = (Object[]) iterator.next();
                Task task = new Task((String) tuple[0]);
                task.setDevice(device);
                task.setData((String) tuple[1]);
                task.setTimestamp(new Date());
                task.setDocNumber((String) tuple[3]);
                task.setReady(false);
                task.setCanceled(false);

                session.close();
                return task;
            }
            return null;
        } catch (PersistenceException pe) {
            logger.error("Can't get next task ", pe);
            return null;
        }
    }

    @Override
    public List<Task> getNotReadyTasks(String device) {
        LinkedList<Task> res = new LinkedList<>();
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Query query = session.createSQLQuery("SELECT uuid, data, timestamp, docNumber FROM json_task WHERE device = :device AND is_ready != 1 AND is_canceled != 1");
            query.setParameter("device", device);

            Iterator iterator= query.list().iterator();
            while (iterator.hasNext()) {
                Object[] tuple = (Object[]) iterator.next();
                Task task = new Task((String) tuple[0]);
                task.setDevice(device);
                task.setData((String) tuple[1]);
                task.setTimestamp(new Date());
                task.setDocNumber((String) tuple[3]);
                task.setReady(false);
                task.setCanceled(false);

                res.add(task);
            }
            session.close();
            return res;
        } catch (PersistenceException pe) {
            logger.error("Can't get next task ", pe);
            return null;
        }
    }

    @Override
    public synchronized boolean cancelTask(String uuid) {
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Query query = session.createSQLQuery("UPDATE json_results SET  is_ready = 1, is_canceled = 1 WHERE uuid = :uuid");
            query.setParameter("uuid", uuid);
            query.executeUpdate();
            tx.commit();
            return true;
        } catch (PersistenceException pe) {
            logger.error("Can't cancel task ", pe);
            return false;
        }
    }

    @Override
    public List<TaskResult> getResultTasksToSend() {
        List<TaskResult> results = new LinkedList<>();
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Query query = session.createSQLQuery("SELECT uuid, timestamp, status, error_code, error_description, result_data, docNumber " +
                                                    "FROM json_results " +
                                                    "WHERE send_code = 0 AND status > 1");

            Iterator iterator= query.list().iterator();
            while(iterator.hasNext()) {
                Object[] tuple = (Object[]) iterator.next();
                TaskResult taskResult = new TaskResult((String) tuple[0]);
                taskResult.setStatus((Integer) tuple[2]);
                taskResult.setErrorCode((Integer) tuple[3]);
                taskResult.setErrorDescription((String) tuple[4]);
                taskResult.setResultData((String) tuple[5]);
                taskResult.setDocNumber((String) tuple[6]);
                results.add(taskResult);
            }
            return results;
        } catch (PersistenceException pe) {
            logger.error("Can't get ResultTasks to send", pe);
            return null;
        }
    }

    @Override
    public boolean updateTaskResultsStatus(List<TaskResult> taskResultList){
        try (Session session = HibernateUtility.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            int i = 0;
            for (TaskResult task : taskResultList){
                Query query = session.createSQLQuery("UPDATE json_results SET  send_code = :send_code, send_res = :send_res WHERE uuid = :uuid");
                query.setParameter("uuid", task.getUuid());
                query.setParameter("send_code", task.getSendCode());
                query.setParameter("send_res", task.getSendRes());
                query.executeUpdate();
                if ( i % 1000 == 0 ) {
                    session.flush();
                    session.clear();
                }
                i++;
            }
            tx.commit();
            return true;
        } catch (PersistenceException pe) {
            logger.error("Can't update TaskResults status", pe);
            return false;
        }

    }
}
