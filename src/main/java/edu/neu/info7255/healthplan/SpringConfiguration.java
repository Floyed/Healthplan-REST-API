package edu.neu.info7255.healthplan;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ComponentScan("edu.neu.info7255.healthplan")
@PropertySource("classpath:application.properties")
public class SpringConfiguration {
}
