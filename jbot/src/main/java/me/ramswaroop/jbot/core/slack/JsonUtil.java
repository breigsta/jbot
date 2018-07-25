package me.ramswaroop.jbot.core.slack;

import java.io.IOException;
import java.util.Collection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.GetResponse;

public class JsonUtil {
	private static ObjectMapper mapper = getObjectMapper();
	private static ObjectMapper prettyMapper = (new ObjectMapper()).registerModule(new JavaTimeModule()).configure(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS, false).enable(SerializationFeature.INDENT_OUTPUT);
	
	private static ObjectMapper getObjectMapper() {
		mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		return mapper;
	}
	
	public static <T> String serialize (final T o) {
		if (o == null) {
			throw new IllegalStateException("Input object is null");
		}
		try {
			return mapper.writeValueAsString(o);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	public static <T> T deSerialize(final String source, final Class<T> targetClass) {
		try {
			return mapper.readValue(source,  targetClass);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	public static <T> T deSerialize(final String source, final JavaType targetType) {
		try {
			return mapper.readValue(source, targetType);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	
	public static <T> T deSerialize(final byte[] array, final Class<T> targetClass) {
		try {
			return mapper.readValue(array, targetClass);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static <T> T deSerialize(final byte[] array, final JavaType targetType) {
		try {
			return mapper.readValue(array, targetType);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static <T> T deSerialize(final GetResponse response, final Class<T> targetClass) {
		return deSerialize(response.getBody(), targetClass);
	}

	public static <T> T deSerialize(final GetResponse response, final JavaType targetType) {
		return deSerialize(response.getBody(), targetType);
	}

	public static <T> String prettyPrint(final T o) {
		if (o == null) {
			throw new IllegalStateException("Input object is null");
		}
		
		try {
			return prettyMapper.writeValueAsString(o);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	@SuppressWarnings("rawtypes")
	public static CollectionType constructCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
		CollectionType ct = mapper.getTypeFactory().constructCollectionType(collectionClass, elementClass);
		return ct;
	}
}
