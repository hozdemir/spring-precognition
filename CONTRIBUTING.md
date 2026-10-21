# Katkı rehberi

Java 17+ ve Maven 3.6.3+ gerekir. Frontend doğrulaması Node 20+ kullanır. Ana geliştirme akışı:

```sh
mvn test
mvn -Psecurity-verification test
./scripts/verify-external.sh
```

Yerel `.tools/` Maven/cache kopyası varsa `./scripts/maven.sh` aynı build'i çalıştırır. Bu başlatıcı standart Maven Wrapper değildir. `PRECOGNITION_MAVEN_REPO` ile depo yolu seçilebilir; varsayılanı çalışma dizininin `.tools/m2` klasörüdür. `scripts/install-local.sh` normal tüketici projelerinin kullandığı `~/.m2/repository` içine kurar.

Örnek uygulamaların component scan'i kütüphane paketlerini kapsamaz. Entegrasyon yalnız starter metadata'sı üzerinden kurulmalıdır. Testleri geçirmek için örneğe `@Import(PrecognitionConfiguration.class)` eklemeyin; bu kurulum hatasını gizler.

Bir düzeltme, hatalı davranışı yeniden üreten test içermelidir. Precognitive istekte controller çağrısı sıfır olmalıdır. Seçilmemiş constraint'lerin gerçekten çalışmadığı sayaçla doğrulanmalıdır. Yalnız HTTP 204 görülmesi yeterli kanıt değildir. Normal submit'in validation, authorization ve başarı akışı korunmalıdır.

HTTP ve browser testleri gerçek loopback portu açar. Ortam port açmayı veya bağımlılık indirmeyi engelliyorsa sonucu geçti diye kaydetmeyin. `VERIFICATION.md` içine çalıştırılan komutu, sınırı ve kesin sürümleri yazın. Surefire ve Playwright raporları generated artifact'tir; Git'e alınmaz.

Public API `precognition-core` içindedir. MVC/JSON/binder bileşenleri kararlı extension API değildir. Jackson 2/3 import'ları kendi Boot adaptörlerinde kalmalıdır. Spring/provider sürümlerini ilgili Boot parent/BOM yönetir. İki starter aynı tüketici projeye eklenmez.

Katkılar [MIT](LICENSE) lisansı altında sunulur. Public release öncesinde tam doğrulama ve sürüm metadata'sı kontrol edilir.
