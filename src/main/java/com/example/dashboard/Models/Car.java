package com.example.dashboard.Models;

public class Car {
private String id;
private Brand brand;

public Car(String id,Brand brand){
    this.id=id;
    this.brand=brand;

}

    public String getId() {
        return id;
    }

    public Brand getBrand() {
        return brand;
    }
}
