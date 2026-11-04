package io.temporal.devdays.essentials.encryption;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.temporal.api.common.v1.Payload;
import io.temporal.payload.codec.PayloadCodecException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** The small helpers the codec slide leaves out: AES-GCM with a random 12-byte IV per payload. */
public final class AesGcm {
  public static final String ENCODING = "binary/encrypted";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  private AesGcm() {}

  public static SecretKey generateKey() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(256);
      return generator.generateKey();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** IV followed by ciphertext. */
  public static byte[] encrypt(SecretKey key, byte[] plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext);
      return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
    } catch (GeneralSecurityException e) {
      throw new PayloadCodecException(e);
    }
  }

  /** Decrypts and parses the Payload that {@link #encrypt} wrapped. */
  public static Payload decrypt(SecretKey key, ByteString data) {
    try {
      byte[] bytes = data.toByteArray();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
      byte[] plaintext = cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
      return Payload.parseFrom(plaintext);
    } catch (GeneralSecurityException | InvalidProtocolBufferException e) {
      throw new PayloadCodecException(e);
    }
  }

  public static boolean isEncrypted(Payload payload) {
    return ENCODING.equals(
        payload.getMetadataOrDefault("encoding", ByteString.EMPTY).toStringUtf8());
  }
}
