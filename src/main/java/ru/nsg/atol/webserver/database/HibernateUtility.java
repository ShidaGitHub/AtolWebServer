package ru.nsg.atol.webserver.database;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtility {
    public static SessionFactory factory;

    private HibernateUtility() {}

    public static synchronized SessionFactory getSessionFactory(){
        if (factory == null)
            factory = new Configuration().configure("hibernate.cfg.xml").buildSessionFactory();

        return factory;
    }
}
