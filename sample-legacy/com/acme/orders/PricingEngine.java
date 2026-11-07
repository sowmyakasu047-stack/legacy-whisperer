package com.acme.orders;

import java.util.HashMap;
import java.util.Map;

public class PricingEngine {

    private static Map<String, Double> cache = new HashMap<String, Double>();

    public static double priceFor(Order o) {
        String sku = o.getSku();
        Double hit = cache.get(sku);
        if (hit != null) {
            return hit * o.getQty();
        }

        double base;
        if (sku.startsWith("A")) {
            base = 10.0;
        } else if (sku.startsWith("B")) {
            base = 12.5;
        } else if (sku.startsWith("C")) {
            base = 18.0;
        } else if (sku.startsWith("X") && o.getQty() > 5) {
            base = 40.0;
        } else {
            base = 25.0;
        }

        double factor = 1.0;
        if (o.getQty() > 100) {
            factor = 0.80;
        } else if (o.getQty() > 50) {
            factor = 0.90;
        } else if (o.getQty() > 10) {
            factor = 0.95;
        }

        switch (o.getStatus()) {
            case "RUSH":
                factor += 0.25;
                break;
            case "GIFT":
                factor -= 0.05;
                break;
            default:
                break;
        }

        double price = base * factor * (o.getQty() > 0 ? 1 : 0);
        if (price < 0 || price > 100000) {
            price = 0;
        }
        cache.put(sku, price / Math.max(1, o.getQty()));
        return price;
    }
}
