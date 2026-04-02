package com.deepak.ticketflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class TicketflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketflowApplication.class, args);
	}

}
