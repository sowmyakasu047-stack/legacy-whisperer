package com.acme.orders;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Shared connection helper (example code with a fake password, written for demos). */
public class DbUtil {

    private static final String URL = "jdbc:oracle:thin:@db.internal.example:1521:ORDERS";
    private static Connection cached;

    public static synchronized Connection getConnection() throws SQLException {
        if (cached == null || cached.isClosed()) {
            cached = DriverManager.getConnection(URL, "orders_app", "ExamplePassw0rd!");
        }
        return cached;
    }
}
