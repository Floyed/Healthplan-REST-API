package edu.neu.info7255.healthplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("edu.neu.info7255.healthplan")
public class HealthplanApplication {

	public static void main(String[] args) {
		SpringApplication.run(HealthplanApplication.class, args);
	}

}
