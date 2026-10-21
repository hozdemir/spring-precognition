#!/bin/sh
# Standalone projects resolve only installed starter JARs/POMs, without reactor source directories.
set -eu
PRECOGNITION_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PRECOGNITION_ROOT"
python3 - <<'PY'
from pathlib import Path
for platform,boot in [('boot3','3.5.16'),('boot4','4.1.1')]:
 root=Path('.tools/consumer-verification')/platform
 source=root/'src/test/java/consumer'
 source.mkdir(parents=True,exist_ok=True)
 (root/'pom.xml').write_text(f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
 <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>{boot}</version><relativePath/></parent>
 <groupId>consumer</groupId><artifactId>standalone-consumer</artifactId><version>1.0</version>
 <properties><java.version>17</java.version></properties><dependencies>
 <dependency><groupId>dev.precognition</groupId><artifactId>precognition-spring-{platform}-starter</artifactId><version>0.1.0-SNAPSHOT</version></dependency>
 <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
 </dependencies></project>''')
 (source/'ConsumerTest.java').write_text('''package consumer;
 import dev.precognition.spring.Precognitive;
 import jakarta.validation.Valid;
 import jakarta.validation.constraints.NotBlank;
 import java.net.URI;
 import java.net.http.*;
 import java.util.concurrent.atomic.AtomicInteger;
 import org.junit.jupiter.api.Test;
 import org.springframework.beans.factory.annotation.Value;
 import org.springframework.boot.autoconfigure.SpringBootApplication;
 import org.springframework.boot.test.context.SpringBootTest;
 import org.springframework.web.bind.annotation.*;
 import static org.junit.jupiter.api.Assertions.*;
 @SpringBootTest(classes=ConsumerTest.Application.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="server.address=127.0.0.1")
 class ConsumerTest {
  @SpringBootApplication @RestController static class Application {
   static final AtomicInteger calls=new AtomicInteger();
   @Precognitive @PostMapping("/users") Object create(@Valid @RequestBody Input body) { calls.incrementAndGet(); return body; }
  }
  record Input(@NotBlank String name,@NotBlank String email) {}
  @Value("${local.server.port}") int port;
  @Test void installedStarterWorksWithoutLibraryImportsOrComponentScan() throws Exception {
   var client=HttpClient.newHttpClient();
   var validate=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/users"))
    .header("Content-Type","application/json").header("Precognition","true").header("Precognition-Validate-Only","name")
    .POST(HttpRequest.BodyPublishers.ofString("{\\"name\\":\\"Ada\\",\\"email\\":\\"\\"}")).build();
   var result=client.send(validate,HttpResponse.BodyHandlers.ofString());
   assertEquals(204,result.statusCode()); assertEquals("true",result.headers().firstValue("Precognition-Success").orElseThrow());
   assertEquals(0,Application.calls.get());
   var submit=HttpRequest.newBuilder(validate.uri()).header("Content-Type","application/json")
    .POST(HttpRequest.BodyPublishers.ofString("{\\"name\\":\\"Ada\\",\\"email\\":\\"ada@example.test\\"}")).build();
   assertEquals(200,client.send(submit,HttpResponse.BodyHandlers.ofString()).statusCode());
   assertEquals(1,Application.calls.get());
  }
 }''')
 # These tests use real instances, no inline mock instrumentation is necessary.
 resources=root/'src/test/resources/mockito-extensions'
 resources.mkdir(parents=True,exist_ok=True)
 (resources/'org.mockito.plugins.MockMaker').write_text('mock-maker-subclass\n')
PY
for PRECOGNITION_PLATFORM in boot3 boot4; do
    ./scripts/maven.sh -B -ntp -f ".tools/consumer-verification/$PRECOGNITION_PLATFORM/pom.xml" test
done
