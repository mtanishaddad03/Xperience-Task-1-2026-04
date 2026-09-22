package com.xperience.hero.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Creates the single drain-control row if it is not there, so the pause switch always exists. */
@Configuration
@RequiredArgsConstructor
public class DrainControlInitialiser {

	private final JdbcTemplate jdbc;

	@Bean
	ApplicationRunner createDrainControlRow() {
		return args -> jdbc.update("insert into drain_control (id, paused) values (?, false) on conflict do nothing",
				DrainControl.ROW_ID);
	}
}
