package com.medtracker.medtracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Levanta el contexto completo, incluida la validación del esquema contra la
// base real, así que solo corre si DATABASE_URL está configurada
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = ".+")
class MedtrackerApplicationTests {

	@Test
	void contextLoads() {
	}

}
