package com.acme.orders;

public class Order {

    private long id;
    private String customerId;
    private String sku;
    private int qty;
    private String status;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
