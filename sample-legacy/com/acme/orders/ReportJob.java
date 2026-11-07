package com.acme.orders;

import java.util.List;

/** Nightly batch job: prints the order total for one customer. */
public class ReportJob {

    public static void main(String[] args) {
        try {
            OrderDao dao = new OrderDao();
            List<Order> orders = dao.findByCustomer(args[0]);
            double total = 0;
            for (Order o : orders) {
                total += PricingEngine.priceFor(o);
            }
            System.out.println("Total: " + total);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
