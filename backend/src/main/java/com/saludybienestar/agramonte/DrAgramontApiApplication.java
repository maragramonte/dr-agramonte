package com.saludybienestar.agramonte;

import com.saludybienestar.agramonte.config.TwilioProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

// La auditoría JPA se habilita en JpaAuditingConfig (separada para no romper @WebMvcTest).
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(TwilioProperties.class)
public class DrAgramontApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DrAgramontApiApplication.class, args);
    }
}
