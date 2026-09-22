package com.xperience.hero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduling is enabled for exactly one task: the outbox drain (KD3). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HeroApplication {

	public static void main(String[] args) {
		SpringApplication.run(HeroApplication.class, args);
	}

}
