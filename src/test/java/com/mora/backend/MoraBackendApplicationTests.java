package com.mora.backend;

import com.mora.backend.repository.BenchmarkRunDetailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MoraBackendApplicationTests {

	@Autowired
	private BenchmarkRunDetailRepository detailRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void printDetails() {
		var details = detailRepository.findAll();
		System.out.println("=== BENCHMARK RUN DETAILS FROM DATABASE ===");
		for (var d : details) {
			System.out.printf("ID: %d | Question: %s | GroundTruth: %s | GeneratedAnswer: %s | Latency: %d ms\n",
					d.getId(), d.getQuestion(), d.getGroundTruth(), d.getGeneratedAnswer(), d.getLatencyMs());
		}
		System.out.println("===========================================");
	}

}
