package com.rayvolt.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.rayvolt.backend.fleet.repository.PostgresFleetRepository;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "spring.flyway.enabled=false",
    "DB_PASSWORD=unused-test-value"
})
class BackendApplicationTests {

    @MockitoBean
    private PostgresFleetRepository repository;

	@Test
	void contextLoads() {
	}

}
