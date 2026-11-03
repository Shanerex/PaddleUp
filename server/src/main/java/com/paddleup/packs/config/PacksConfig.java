package com.paddleup.packs.config;

import com.paddleup.packs.model.PackRegistry;
import com.paddleup.packs.repository.PackFileRepository;
import com.paddleup.packs.service.DataAdapter;
import com.paddleup.packs.service.PackLoader;
import com.paddleup.packs.util.PackParser;
import java.nio.file.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads the packs once at startup; any failure here aborts the application. */
@Configuration
@EnableConfigurationProperties(PacksProperties.class)
public class PacksConfig {

  @Bean
  PackRegistry packRegistry(PacksProperties properties, ObjectProvider<DataAdapter> adapters) {
    var mapper = PackParser.newMapper();
    var repository = new PackFileRepository(Path.of(properties.dir()), mapper);
    return new PackLoader(repository, new PackParser(mapper), adapters.orderedStream().toList())
        .load();
  }
}
