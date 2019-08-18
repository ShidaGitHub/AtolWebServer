package ru.nsg.atol.webserver.database;

public class DBProvider {
    public static DatabaseDAO db = new SQLServerDAOImpl();
    private DBProvider() {}
}
