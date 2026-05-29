package com.saludybienestar.agramonte;

import com.saludybienestar.agramonte.config.TwilioProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableConfigurationProperties(TwilioProperties.class)
public class DrAgramontApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DrAgramontApiApplication.class, args);
    }
}
