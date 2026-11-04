package io.temporal.devdays.essentials.encryption;

import static io.temporal.devdays.essentials.encryption.AesGcm.decrypt;
import static io.temporal.devdays.essentials.encryption.AesGcm.encrypt;
import static io.temporal.devdays.essentials.encryption.AesGcm.isEncrypted;

import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.payload.codec.PayloadCodec;
import java.util.List;
import javax.crypto.SecretKey;

// slide: 20-encryption.md "A codec in practice"
public record EncryptionCodec(SecretKey key) implements PayloadCodec {
  private static final String ENCODING = "binary/encrypted";

  @Override
  public List<Payload> encode(List<Payload> payloads) {
    return payloads.stream()
        .map(
            payload ->
                Payload.newBuilder()
                    .putMetadata("encoding", ByteString.copyFromUtf8(ENCODING))
                    .setData(ByteString.copyFrom(encrypt(key, payload.toByteArray())))
                    .build())
        .toList();
  }

  @Override
  public List<Payload> decode(List<Payload> payloads) {
    return payloads.stream()
        .map(payload -> isEncrypted(payload) ? decrypt(key, payload.getData()) : payload)
        .toList();
  }
}
// end slide
