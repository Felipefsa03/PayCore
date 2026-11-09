package com.paycore.financialcore.app.outbox.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Apoio aos testes de contrato: carrega e valida os JSON Schemas de contracts/events. */
final class EventContracts {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private EventContracts() {
    // utility class
  }

  static Path directory() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve("contracts").resolve("events");
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("contracts/events not found from " + System.getProperty("user.dir"));
  }

  static JsonNode schema(String fileName) {
    try {
      return MAPPER.readTree(directory().resolve(fileName).toFile());
    } catch (IOException exception) {
      throw new IllegalStateException("cannot read contract " + fileName, exception);
    }
  }

  static String text(String fileName) {
    try {
      return Files.readString(directory().resolve(fileName));
    } catch (IOException exception) {
      throw new IllegalStateException("cannot read contract " + fileName, exception);
    }
  }

  static JsonNode parse(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException exception) {
      throw new IllegalStateException("cannot parse json", exception);
    }
  }

  static void assertMatchesSchema(String schemaFile, String json) {
    JsonSchemaFactory factory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    JsonSchema jsonSchema = factory.getSchema(schema(schemaFile));
    Set<ValidationMessage> errors = jsonSchema.validate(parse(json));
    assertThat(errors).as("schema violations for %s", schemaFile).isEmpty();
  }
}
