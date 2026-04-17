package com.fsd10.merry_match_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "scheduler.subscription.enabled=false")
class MerryMatchBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
