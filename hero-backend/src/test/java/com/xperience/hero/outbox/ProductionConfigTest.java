package com.xperience.hero.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The values the application really runs with — the test profile shortens the drain's timings. */
class ProductionConfigTest {

	private HeroProperties load() throws Exception {
		List<PropertySource<?>> sources = new YamlPropertySourceLoader()
				.load("application.yml", new ClassPathResource("application.yml"));
		MutablePropertySources all = new MutablePropertySources();
		sources.forEach(all::addLast);
		all.addLast(new MapPropertySource("empty", java.util.Map.of()));
		return new Binder(ConfigurationPropertySources.from(all))
				.bind("hero", HeroProperties.class).get();
	}

	@Test
	void jdbcAndJpaUseTheSameSchema() throws Exception {
		// JPA reads hibernate.default_schema; plain JDBC follows the connection's search path. If they differ,
		// the application starts and then fails on the first plain statement.
		List<PropertySource<?>> sources = new YamlPropertySourceLoader()
				.load("application.yml", new ClassPathResource("application.yml"));
		MutablePropertySources all = new MutablePropertySources();
		sources.forEach(all::addLast);
		String url = (String) all.get("application.yml")
				.getProperty("spring.datasource.url");
		String jpaSchema = (String) all.get("application.yml")
				.getProperty("spring.jpa.properties.hibernate.default_schema");

		assertThat(url).contains("currentSchema=" + jpaSchema);
	}

	@Test
	void theDrainAndLimitsMatchTheDesign() throws Exception {
		HeroProperties properties = load();

		assertThat(properties.drain().retryDelays())
				.containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
		assertThat(properties.drain().sendTimeout()).isEqualTo(Duration.ofSeconds(10));
		assertThat(properties.drain().reclaimAfter()).isEqualTo(Duration.ofMinutes(1));
		assertThat(properties.drain().batchSize()).isEqualTo(10);
		assertThat(properties.drain().enabled()).isTrue();
		assertThat(properties.limits().invitationsPerHostPerDay()).isEqualTo(1000);
		assertThat(properties.limits().managementLinksPerAddressPerDay()).isEqualTo(5);
	}
}
