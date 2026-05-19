package p5laris.mission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class MissionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MissionApplication.class, args);
    }

}
