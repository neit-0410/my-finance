package com.neit.personal_finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@EnableJpaRepositories(
		basePackages = "com.neit.personal_finance.repositories.jpa"
)
@EnableMongoRepositories(
		basePackages = "com.neit.personal_finance.repositories.mongo"
)
public class PersonalFinanceApplication {

	@RequestMapping("/")
	public String home() {
		return "FINANCE APPLICATION";
	}

	public static void main(String[] args) {
		SpringApplication.run(PersonalFinanceApplication.class, args);
	}

}
