package model;

public interface Bill {
    Product itemList[] = new Product[100];

    Product getItemById(int id);

    void addItem(Product product);

    float calculateTotal();
}
