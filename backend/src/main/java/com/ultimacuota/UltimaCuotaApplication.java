package com.ultimacuota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UltimaCuotaApplication {

    public static void main(String[] args) {
        SpringApplication.run(UltimaCuotaApplication.class, args);
    }
}
