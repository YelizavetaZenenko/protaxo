package com.example.protaxo;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class ProtaxoApplication {

	public static void main(String[] args) {
		// Сервер (і local Docker) працюють у UTC за замовчуванням — без цього
		// час у БД (TIMESTAMP WITHOUT TIME ZONE, конвертація йде через JVM
		// default zone) і в UI (Thymeleaf #temporals, ContractMapper) відрізнявся
		// від реального київського на 2-3 години. Встановлюється до
		// SpringApplication.run(), щоб застосувалось раніше, ніж будь-який
		// компонент закешує ZoneId.systemDefault().
		TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"));
		SpringApplication.run(ProtaxoApplication.class, args);
	}

}
