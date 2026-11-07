package com.acme.orders;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class OrderDao {

    public List<Order> findByCustomer(String customerId) throws SQLException {
        Connection c = DbUtil.getConnection();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(
                "SELECT id, sku, qty, status FROM orders WHERE customer_id = '" + customerId + "'");
        List<Order> result = new ArrayList<>();
        while (rs.next()) {
            Order o = new Order();
            o.setId(rs.getLong("id"));
            o.setCustomerId(customerId);
            o.setSku(rs.getString("sku"));
            o.setQty(rs.getInt("qty"));
            o.setStatus(rs.getString("status"));
            result.add(o);
        }
        return result;
    }

    public void save(Order o) throws SQLException {
        Connection c = DbUtil.getConnection();
        Statement st = c.createStatement();
        st.executeUpdate("INSERT INTO orders (customer_id, sku, qty, status) VALUES ('"
                + o.getCustomerId() + "','" + o.getSku() + "'," + o.getQty() + ",'NEW')");
    }
}
