package com.jobtrack;

import org.springframework.boot.SpringApplication;

public class TestJobTrackApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobTrackApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
