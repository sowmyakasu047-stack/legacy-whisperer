package com.acme.orders;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OrderServlet extends HttpServlet {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd");
    private OrderDao dao = new OrderDao();
    private int requestCount = 0;
    private String lastCustomer;

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requestCount++;
        String customerId = req.getParameter("customerId");
        lastCustomer = customerId;
        System.out.println("GET orders for " + customerId + " at " + FMT.format(new Date()));
        PrintWriter out = resp.getWriter();
        try {
            List<Order> orders = dao.findByCustomer(customerId);
            for (Order o : orders) {
                out.println(o.getId() + "," + o.getStatus() + "," + PricingEngine.priceFor(o));
            }
        } catch (Exception e) {
            // ignore
        }
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requestCount++;
        Order o = new Order();
        o.setCustomerId(req.getParameter("customerId"));
        o.setSku(req.getParameter("sku"));
        o.setQty(Integer.parseInt(req.getParameter("qty")));
        o.setStatus("NEW");
        try {
            dao.save(o);
            resp.setStatus(201);
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
        }
    }
}
