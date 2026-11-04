package com.chick.app.config;

import java.math.BigDecimal;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Money and weights go out as strings in plain notation ({@code "838.3838"}), so clients never
 * read them as floating-point numbers.
 */
@Configuration
public class JsonConfig {

	@Bean
	JsonMapperBuilderCustomizer decimalsAsStrings() {
		SimpleModule module = new SimpleModule("decimals-as-strings");
		module.addSerializer(BigDecimal.class, new PlainDecimalSerializer());
		return builder -> builder.addModule(module);
	}

	static final class PlainDecimalSerializer extends StdSerializer<BigDecimal> {

		PlainDecimalSerializer() {
			super(BigDecimal.class);
		}

		@Override
		public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
			generator.writeString(value.toPlainString());
		}
	}
}
