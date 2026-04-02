package com.fsd10.merry_match_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.fsd10.merry_match_backend.config.JwtProperties;
import com.fsd10.merry_match_backend.config.SupabaseProperties;

@SpringBootApplication
@EnableConfigurationProperties({ SupabaseProperties.class, JwtProperties.class })
public class MerryMatchBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MerryMatchBackendApplication.class, args);
	}

}
