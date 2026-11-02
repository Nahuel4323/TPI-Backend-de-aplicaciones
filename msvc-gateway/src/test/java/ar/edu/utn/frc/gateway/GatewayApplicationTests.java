package ar.edu.utn.frc.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"app.gateway.internal-token=test-only-internal-token", "spring.config.import="})
class GatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
