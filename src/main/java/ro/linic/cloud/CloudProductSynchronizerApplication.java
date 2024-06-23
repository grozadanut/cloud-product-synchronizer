package ro.linic.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class CloudProductSynchronizerApplication {

	public static void main(final String[] args) {
		SpringApplication.run(CloudProductSynchronizerApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate(final RestTemplateBuilder builder) {
	    return builder.build();
	}
}
