package com.example.dashboard.Models;

public class Car {
private String id;
private Brand brand;
private String modelName;

public Car(String id,Brand brand,String modelName){
    this.id=id;
    this.brand=brand;
    this.modelName=modelName;

}

    public String getId() {
        return id;
    }

    public Brand getBrand() {
        return brand;
    }

    public String getModelName() {
        return modelName;
    }
}
