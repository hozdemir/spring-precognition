# Changelog

## Unreleased — 0.1.0-SNAPSHOT

- Public annotation, mode, request context ve authorization SPI'sini ayrı core modülüne taşı.
- Spring MVC yürütmesi ile Boot 3/Jackson 2 ve Boot 4/Jackson 3 auto-configuration modüllerini ayır.
- İki sürüme uygun bağımlılık starter'ları ve harici paketlerden çalışan örnek uygulamalar ekle.
- Başlangıçta endpoint, model, güvenlik, MVC adapter, validator ve JSON converter uygunluğunu denetle.
- FULL/SELECTIVE, ordinary DTO/scalar tipler, JSON wire-name/naming strategy ve sınırlı nested graph desteği ekle.
- Ortak authorizer, request context accessor, disabled guard, normal hata politikası ve kaynak sınırlarını uygula.
- İsteğe bağlı ObservationRegistry entegrasyonunda düşük kardinaliteli scope/status gözlemleri üret.
- Resmi React/Vue doğrulaması, kurulmuş JAR tüketicileri, CI, kurulum/yayın belgeleri ve MIT lisansı ekle.
- ERROR dispatch ve eksik filtre korumasında ortak başlık ayrıştırmasıyla hatalı precognitive isteklerin normal action'a düşmesini engelle.
- Mevcut WebMvcRegistrations bean'ini koru; opt-in endpoint ile uyumsuz özel adapter'ı başlangıçta reddet.
- Unknown-length JSON gövdesinin trailing içeriğini de boyut sınırına dahil et; body okumayı endpoint yetkilendirmesinden sonra ve binding'den önce sınırla.
- Tarayıcı örneklerinde authentication token'ını backend ile eşleştir; iki platform için dispatch, MVC kayıt ve body limit regresyonları ekle.
- Boot 4 library modüllerinde reactor genelindeki hedefli Surefire test seçimini destekle; iki örnek uygulamada eksik test seçiminin sessizce geçmesini engelle.
- README'yi motivasyon, Laravel referansları, kurulum ve kullanım örnekleri korunarak İngilizceye çevir.

Kaynak sürümü henüz Maven Central'a yayımlanmadı. Kesin test sonuçları ve çalıştırılamayan kontroller `VERIFICATION.md` içindedir.
