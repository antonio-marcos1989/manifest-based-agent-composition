package com.br.honeycombframework.config;

import com.br.honeycombframework.model.AgentManifest;
import com.br.honeycombframework.model.AlaSettings;
import com.br.honeycombframework.model.AgentInvocationMetrics;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;

@Configuration
public class MongoConvertersConfig {

    private static final ObjectMapper MONGO_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new AgentRoleReadConverter(),
                new AgentInvocationMetricsReadConverter(),
                new AlaSettingsReadConverter()
        ));
    }

    @ReadingConverter
    static class AgentRoleReadConverter implements Converter<String, AgentManifest.Role> {
        @Override
        public AgentManifest.Role convert(String source) {
            return AgentManifest.Role.fromValue(source);
        }
    }

    @ReadingConverter
    static class AgentInvocationMetricsReadConverter implements Converter<Document, AgentInvocationMetrics> {
        @Override
        public AgentInvocationMetrics convert(Document source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return MONGO_MAPPER.convertValue(source, AgentInvocationMetrics.class);
        }
    }

    @ReadingConverter
    static class AlaSettingsReadConverter implements Converter<Document, AlaSettings> {
        @Override
        public AlaSettings convert(Document source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return MONGO_MAPPER.convertValue(source, AlaSettings.class);
        }
    }
}
