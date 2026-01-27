package com.qualtech_ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "azure")
public class AzureProperties {

    private Storage storage = new Storage();
    private Speech speech = new Speech();
    private Language language = new Language();
    private Face face = new Face();

    @Data
    public static class Storage {
        private String connectionString;
        private String containerName;
    }

    @Data
    public static class Speech {
        private String key;
        private String region;
    }

    @Data
    public static class Language {
        private String key;
        private String endpoint;
    }

    @Data
    public static class Face {
        private String key;
        private String endpoint;
        private boolean enabled;
    }
}
