package com.example.protaxo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ProtaxoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProtaxoApplication.class, args);
	}

}
