package io.temporal.devdays.essentials.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.converter.DataConverterException;
import io.temporal.common.converter.JacksonJsonPayloadConverter;
import io.temporal.common.converter.PayloadConverter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Optional;

/** The SDK's Jackson setup, plus one feature: a ZonedDateTime keeps its zone ID. */
// slide: 19-serialization.md "A custom payload converter"
public class ZonedJsonConverter implements PayloadConverter {
  private final ObjectMapper mapper =
      JacksonJsonPayloadConverter.newDefaultObjectMapper()
          .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);

  public String getEncodingType() {
    return "json/zoned";
  }

  public Optional<Payload> toData(Object value) {
    try {
      return Optional.of(
          Payload.newBuilder()
              .putMetadata("encoding", ByteString.copyFromUtf8(getEncodingType()))
              .setData(ByteString.copyFrom(mapper.writeValueAsBytes(value)))
              .build());
    } catch (JsonProcessingException e) {
      throw new DataConverterException(e);
    }
  }
// end slide

  // part: 19-serialization.md "A custom payload converter: reading it back" 1
  public <T> T fromData(Payload payload, Class<T> type, Type genericType) {
    try {
      return mapper.readValue(
          payload.getData().toByteArray(), mapper.constructType(genericType));
    } catch (IOException e) {
      throw new DataConverterException(e);
    }
  }
  // end part
}
