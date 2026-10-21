# Güvenlik

Öncelikli güvenlik özellikleri: precognitive isteklerin controller'a ulaşmaması; normal authorization/CSRF davranışının korunması; desteklenmeyen imzaların başlangıçta reddedilmesi; body/header/response sınırlarının uygulanmasıdır.

Henüz yayımlanmış kararlı sürüm ve desteklenen eski sürüm serisi yoktur. Güncel sürüm matrisi `README.md` içindedir.

Bir açık bulursanız geçerli kimlik bilgisi veya gerçek kullanıcı verisi paylaşmayın. GitHub deposunda private vulnerability reporting etkinse Security sekmesinden özel rapor gönderin. Etkin değilse yeniden üretim ayrıntılarını kamuya açmadan maintainer ile özel iletişim kanalı belirleyin. Rapor; kesin sürümleri, beklenen/gerçek davranışı ve mümkünse controller'ın çalıştığını gösteren minimal test içermelidir.

Kütüphane authentication sistemi kurmaz, CSRF'yi kapatmaz ve otomatik CORS açmaz. Constraint, converter ve authorizer kodu uygulama yetkileriyle çalışır. Son submit'te iş kuralları, DB constraint'leri ve yetkilendirme yeniden uygulanmalıdır.
