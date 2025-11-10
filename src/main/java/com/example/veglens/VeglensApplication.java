package com.example.veglens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.boot.context.properties.ConfigurationPropertiesScan
public class VeglensApplication {

	public static void main(String[] args) {
		SpringApplication.run(VeglensApplication.class, args);
	}

}
