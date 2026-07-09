package com.mora.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MoraBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoraBackendApplication.class, args);
	}

}
