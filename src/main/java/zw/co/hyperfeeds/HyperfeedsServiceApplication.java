package zw.co.hyperfeeds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HyperfeedsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(HyperfeedsServiceApplication.class, args);
	}

}
