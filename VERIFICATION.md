# Doğrulama durumu

Tarih: **21 Ekim 2026**. Kaynak ve artifact sürümü: **`0.1.0-SNAPSHOT`**, group ID: `dev.precognition`. Bu kayıt mevcut modüler implementasyon içindir. Henüz commit SHA'sı yoktur; aşağıdaki JAR hash'leri sınanan paketleri tanımlar. Maven Central yayını yapılmamıştır.

## Sonuç

Kullanıcının kendi terminalinde çalıştırdığı `./scripts/verify-external.sh`, **18:58–19:01 Europe/Istanbul** aralığında **194,539 saniyede exit 0** ile tamamlandı. Canonical POM'lar, gerçek Maven bağımlılıkları ve üretilip yerel depoya kurulmuş JAR'lar kullanıldı. Bu koşuda geçici Servlet API ikamesi yapılmadı.

| Test grubu | Boot 3 | Boot 4 | Toplam |
| --- | --- | --- | --- |
| MVC, model, startup, starter ve review regresyonları | 78 | 78 | 156 |
| Gerçek TCP/HTTP (Java / embedded Tomcat) | 16 | 16 | 32 |
| Gerçek SecurityFilterChain (MockMvc) | 8 | 8 | 16 |
| Resmi Laravel core sözleşme replay | 7 | 7 | 14 |
| Resmi core istemcisiyle canlı HTTP | 9 | 9 | 18 |
| Resmi React/Vue, gerçek tarayıcı (Playwright) | 10 | 10 | 20 |
| Kurulmuş JAR'larla bağımsız tüketici, gerçek HTTP | 1 | 1 | 2 |
| **Toplam** | **129** | **129** | **258** |

Test gruplarında **failure/error/skip yok**. İki Playwright raporunda da `expected=10`, `unexpected=0`, `skipped=0`, `flaky=0`. Altı runtime JAR içerik kontrolü ve Maven package/install aşaması ayrıca başarılı; bunlar 258 test sayısına eklenmedi.

Son package/install aşamasındaki `Tests are skipped` çıktısı `-DskipTests install` komutuna aittir: testler önceki aşamalarda çalışmıştır. Bu, yukarıdaki testlerin atlandığı anlamına gelmez.

## Sınanan sürümler

| Bileşen | Boot 3 tüketicisi | Boot 4 tüketicisi |
| --- | --- | --- |
| Java | 17.0.20 | 17.0.20 |
| Spring Boot | 3.5.16 | 4.1.1 |
| Spring MVC | 6.2.19 | 7.0.9 |
| Jackson databind | 2.21.4 | 3.1.5 |
| Hibernate Validator | 8.0.3.Final | 9.1.3.Final |
| Spring Security | 6.5.11 | 7.1.1 |
| Embedded Tomcat | 10.1.55 | 11.0.24 |

Resmi `laravel-precognition`, `laravel-precognition-react` ve `laravel-precognition-vue` sürümleri 2.1.0; React 19.2.0, Vue 3.5.27, Playwright 1.64.1. npm paketleri lockfile üzerinden `npm ci` ile kuruldu. Browser yapılandırması varsayılan olarak Chrome kullanır; tarayıcının tam build numarası sonuç JSON'unda kayıtlı değildir.

## Doğrulanan davranışlar

- Auto-configuration imports üzerinden iki starter'ın kurulması. Örnek uygulama library paketlerini component scan'e almaz veya elle import etmez.
- Controller çağrılmadan full/selected validation; seçilmeyen pahalı validator'ın çalışmaması; sonraki normal submit'in yeniden full validation yapması.
- FULL-only class-level kurallar, `_form`, ordinary DTO ve BigDecimal/Boolean/LocalDate/UUID gibi scalar tipler.
- `@JsonProperty`, aktif snake_case stratejisi, wire adlarıyla seçim ve normal/precognitive hata eşlemesi.
- Nested leaf/parent seçimleri, overlap tekilleştirme, optional/required null parent ve full cascade.
- Normal ve precognitive yolda ortak authorizer; body bağlamadan önce erişim reddi.
- Disabled modda 412; normal submit ve authorizer'ın devam etmesi. `normal-errors=preserve` davranışı.
- Bilinen Content-Length ve unknown-length gövdelerde boyut sınırı; küçük JSON + büyük whitespace kuyruğunun 413 alması; sınırdaki geçerli gövdenin kabul edilmesi; en fazla limit+1 byte okuma ve authorizer reddinde gövdenin hiç okunmaması. Header ve response limitleri; kısmi 422 yerine configuration error.
- Mevcut Vary ve `*` korunması; malformed body, media/encoding, Accept ve dispatch reddi.
- Validator hatasında 500 ve hata ayrıntılarının/payload'ın yanıt gövdesine sızmaması.
- RequestBodyAdvice zinciri, uygulamanın global exception advice'ı ve controller/servis exception politikasının korunması.
- Normal/full mesaj eşdeğerliği ve Türkçe/İngilizce mesajların paralel 20 istekte izolasyonu.
- Request context'in request dışında boş olması; ObservationRegistry scope/status etiketleri ve kapatılabilmesi.
- Gerçek SecurityFilterChain: HTTP Basic, CSRF, normal method-security proxy izin/reddi ve opt-in olmayan secured route'un precognitive reddi.
- Unsupported collection, alias, sınıf kuralı/SELECTIVE, method security, arbitrary proxy, binder, custom MVC validator/converter, adapter conflict, eksik @Valid ve HTTP metodu için fail-fast.
- ERROR dispatch ve eksik filtre durumunda aktif/boşluklu/çoklu/geçersiz başlıklar ile orphan seçimin normal controller'a düşmemesi; disabled modda da action sayacının sıfır kalması.
- Opt-in endpoint bulunmayan tüketicide uygulamanın WebMvcRegistrations adapter/mapping/exception resolver'ının enabled ve disabled modlarda korunması; uyumsuz opt-in endpoint ile açık startup hatası verilmesi.

Statik desteklenmeyen yapıların çoğu artık runtime 400 üreten eski örnekler yerine izole başlangıç context'lerinde test edilir. Class-level kural için FULL modunun olumlu yolu ayrıca test edilir.

Gerçek HTTP grubunda büyük chunked JSON, küçük geçerli JSON + büyük trailing whitespace ve tam 65.536 byte sınırındaki geçerli gövde sınandı. Fazla gövde 413 üretirken validator/controller sayacı sıfır kaldı; sınırdaki geçerli istek 204 aldı.

React ve Vue için hata gösterme/temizleme, normal submit, nested alan, erken 401 callback'i ve ayrı origin'de 422 → 204 → 401 akışı iki backend ile geçti. Browser testleri backend yanıtlarını taklit etmez; gerçek HTTP ve örnek sayaçlarını kullanır. CORS ayarı örnek uygulamaya aittir; starter kendiliğinden CORS açmaz.

## Artifact ve bağımsız tüketici kanıtı

Canonical `install` root parent'ı, altı kütüphane modülünü ve yerel örnekleri **workspace `.tools/m2`** deposuna kurdu. Bu koşu standart `~/.m2/repository` kurulumunu ayrıca sınamaz; o yol için `scripts/install-local.sh` veya standart `mvn install` kullanılır.

`scripts/verify-artifacts.py` altı JAR için kök LICENSE ile aynı MIT metnini, demo/verification/JUnit sınıflarının dışarıda kalmasını, starter'larda sınıf bulunmamasını, auto-configuration imports ve configuration metadata varlığını doğruladı.

İki bağımsız consumer projesinin raporlanan classpath'inde kütüphaneler yalnız `.tools/m2/dev/precognition/.../*.jar` olarak bulunuyor; kütüphanelerin reactor `target/classes` dizinleri yok. Uygulamalar yalnız starter bağımlılığıyla ve normal auto-configuration ile açıldı. Seçili validation HTTP üzerinden 204 ve sıfır action çağrısı; normal submit 200 ve bir action çağrısı üretti. Kütüphane component scan'i genişletilmedi, configuration elle import edilmedi.

Üretilen altı runtime JAR'ın SHA-256 değerleri aşağıdadır. `.tools/m2` içindeki kurulu kopyalarla byte düzeyinde eşitlik ayrıca kontrol edildi.

| Artifact | SHA-256 |
| --- | --- |
| precognition-core | `9603580072031410678be908965f38ecc54403c091001510cb57286c5196adff` |
| precognition-spring-webmvc | `2976cb51250d41a2ef36a287e4126cfcd95cca5120120d4bb70906c99d691480` |
| precognition-spring-boot3-autoconfigure | `04b09edbd77d343a4767a4ece3acce26146e0366c3221b8b5602e2a654a7747b` |
| precognition-spring-boot3-starter | `43198fbcb1887fa74da5ef630f07db3940a81c4b6f1917f27d2a5ffe4c07bef4` |
| precognition-spring-boot4-autoconfigure | `9862cf23f3992e300fc5e83ade4b62d3c004a14f39c7bc1825d8e04718f419ba` |
| precognition-spring-boot4-starter | `dba3169b6007c4a3e2609afc75aa1414829944d153d9a36717e827d4dca78156` |

## Kanıtların konumu

- Kullanıcının exit 0 ile biten tam dış script çıktısı; Node replay ve canlı HTTP adımları burada raporlanır. Script `set -eu` kullanır ve ilk hatada durur.
- `examples/boot3/target/surefire-reports/`, `examples/boot4/target/surefire-reports/`: her platform için 78 normal Java testi ve 16 HTTP testi.
- `examples/boot3/target/security-verification/surefire-reports/`, Boot 4 eşdeğeri: 8'er Security testi.
- `target/browser-results/boot3/report.json`, Boot 4 eşdeğeri: 10'ar başarılı browser testi.
- `.tools/consumer-verification/boot3/target/surefire-reports/`, Boot 4 eşdeğeri: 1'er gerçek HTTP installed-JAR consumer testi ve classpath.
- `target/validation/boot3-laravel-fixtures.json`, Boot 4 eşdeğeri: resmi core replay girdileri.

Maven XML ve Playwright JSON raporları ayrıca okunarak sayılar kontrol edildi. Node test sayıları script akışı, test kaynakları ve başarılı dış koşuyla eşleştirildi. Raw/generated raporlar, JAR'lar, `.tools/`, `target/` ve `node_modules/` Git'e alınmaz.

## Önceki engeller ve düzeltmeler

- Eski tek modüllü sürümün 167 testi ve ilk modüler kaynak kontrolleri tarihçedir; güncel 258 sonucun içine tekrar sayılmadı.
- İlk offline kaynak kontrollerindeki eksik Servlet API için kullanılan geçici provided API yöntemi, eski kaynak kanıtıydı. Son başarılı dış koşu canonical bağımlılıkları kullanır.
- İncelemede ERROR dispatch başlık koruması, browser token uyuşmazlığı, mevcut WebMvcRegistrations'ın kaybı ve unknown-length body suffix sınırı düzeltildi. İki platformdaki regresyonlar ve ilgili HTTP/browser/package akışları artık geçti.
- İlk dış script HTTP testleri başlamadan test içermeyen Boot 4 auto-configuration modülündeki Surefire seçimi yüzünden durdu. Boot 4 library modülleri hedefli test seçimine izin verecek şekilde düzeltildi; örnek uygulamalarda eksik test seçimi hâlâ hata verir. Yanlış test adıyla iki platformda da beklenen ret ayrıca kontrol edilmişti.
- Agent sandbox'ındaki socket izni ve ilk offline install-plugin cache eksikliği, o ortamda kontrollerin tamamlanmasını engellemişti. Kullanıcının son dış koşusu bu aşamaları gerçek ağ/socket erişimiyle tamamladı. Önceki başarısız/çalıştırılmamış aşamalar geriye dönük başarılı sayılmadı.

## Kalan yayın işleri ve sınırlar

Yerel Java 17 doğrulama matrisinde bekleyen aşama yok. **Java 21 ve GitHub Actions henüz çalıştırılmadı**; workflow'un hazırlanmış olması CI başarısı değildir. Boot 4 test fixture'ındaki `extendMessageConverters` kullanımı deprecation uyarısı üretiyor; mevcut koşuyu başarısız kılmadı, sonraki framework yükseltmelerinde ele alınmalı.

Maven Central için final namespace sahipliği, proje/developer/SCM metadata'sı, kaynak/Javadoc JAR'ları, imzalama, yayın hesabı ve release sürümü hâlâ hazırlanmalıdır. Bu doğrulama public yayın yapıldığı veya 1.0 uyumluluk taahhüdü verildiği anlamına gelmez. Sonuçlar sınanan sürümlere ve mevcut destek kapsamına aittir; yük/dayanıklılık, bütün Servlet container'ları veya tüm Spring özelleştirmeleri için kanıt değildir.

Collection/wildcard, özel gruplar, multipart, WebFlux, native/AOT ve action çağrısı olmadan method-security ifade değerlendirmesi kapsam dışındadır. Desteklenmeyen opt-in yapılar sessiz kısmi doğrulama yerine başlangıçta reddedilir.

Tam koşuyu tekrar üretmek için Java 17+, Maven, Node, Python, ağ/loopback erişimi ve uygun Playwright tarayıcısıyla:

```sh
./scripts/verify-external.sh
```

[Kurulum](docs/INSTALLATION.md) ve [HTTP sözleşmesi](docs/PROTOCOL.md) ayrıntıları depoda bulunur.
