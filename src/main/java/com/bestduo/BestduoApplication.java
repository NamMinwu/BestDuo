package com.bestduo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class BestduoApplication {

	public static void main(String[] args) {
		SpringApplication.run(BestduoApplication.class, args);
	}

}
