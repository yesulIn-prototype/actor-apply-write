package kr.yesulin.actor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ActorApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ActorApiApplication.class, args);
	}

}
