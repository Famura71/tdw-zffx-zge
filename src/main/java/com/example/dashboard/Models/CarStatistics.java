package com.example.dashboard.Models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CarStatistics {

    public static Map<Brand, Integer> countByBrand(List<Car> cars){
        Map<Brand, Integer> result = new HashMap<>();

        for(Car car:cars){
            Brand brand = car.getBrand();

            if(result.containsKey(brand)){
                result.put(brand,result.get(brand)+1);
            }else{
                result.put(brand,1);
            }
        }
return  result;
    }
}
