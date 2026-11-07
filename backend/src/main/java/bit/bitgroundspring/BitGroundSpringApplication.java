package bit.bitgroundspring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class BitGroundSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(BitGroundSpringApplication.class, args);
    }

}
