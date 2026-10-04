# Yayınlama hazırlığı

Bu klasör 2026-10-04 tarihinde port.12 kaynaklarından ayrı bir yerel Git deposu
olarak hazırlandı. Yayın deposu RPM147 hesabında oluşturuldu:
[toms-trading-network-1.12.2-port](https://github.com/RPM147/toms-trading-network-1.12.2-port).
Kaynak yükleme işlemi tek başına paket sürümü veya platform onayı değildir.
Orijinal çalışma klasörü, canlı instance ve özel arşivler bu depoya taşınmadı.

## Depoya dahil edilenler

- Forge-1.12.2 kaynakları, testleri, kaynak görselleri ve çevirileri.
- Gradle wrapper ve yapılandırması; wrapper JAR'ı dağıtım JAR'ı değildir.
- Orijinal MIT lisansı ve kaynak/provenance atıfları.
- Halka açık README, changelog, kullanım, build, mimari ve backfill belgeleri.
- İki bağımsız offline scanner/test Python betiği.

## Dahil edilmeyenler

Eski Git geçmişi/remote, Fabric/NeoForge kodu, eski rozet/bağış ayarları, kişisel
AGENT/PLAN dosyaları, ham doğrulama arşivleri, oyun kayıtları, modpaketi/config,
loglar, crash raporları ve üçüncü taraf mod JAR'ları kopyalanmadı. Yerel instance
bağımlı smoke yardımcıları da alınmadı. Genel geliştirme run görevleri build'de kalır.
Derleme çalıştırılırsa oluşan `build` ve `.gradle` klasörleri Git tarafından dışlanır.

## İlk commit/push öncesi

1. GitHub kullanıcı adını ve yeni repo adını belirle. Yeni repo oluştururken
   README/lisans/gitignore otomatik ekleme; bunlar burada mevcut.
2. Profilde commit e-postası gizliliğini kontrol et; istersen GitHub no-reply adresini
   yerel Git kimliğinde kullan. Başkasının kimliğini veya tokenını kopyalama.
3. Kamuya açık bakım sorumlusu bilgisini CREDITS/README'ye ekle.
4. `mcmod.info` halen port.12'nin **orijinal proje URL'sini** içerir; bu kaynak
   dosya davranış/sürüm bütünlüğünü korumak için değiştirilmedi. Yeni port repo URL'si
   belli olduğunda metadata URL/isim/credits alanlarını güncelle, upstream atfını koru.
5. Metadata/JAR içeriği değişirse **yeni sürüm numarası** kullan; farklı bir JAR'ı
   tekrar port.12 diye yayınlama. `build.gradle` ve `BuildInfo.VERSION` birlikte değişmeli.
6. Gerçek ekran görüntüsü ve kullanım hakkı açık, sana ait ikon hazırla.
7. Kaynak diff'ini incele; hiçbir API anahtarı, oyuncu verisi veya yerel dosya yolu
   paylaşılmadığını doğrula. Otomatik tarama eksiksiz gizlilik garantisi değildir.
8. `.gitignore` derleme çıktısını dışlar; `git add --dry-run .` ile dosya listesini gör.

Yalnız **bu yeni deponun kökünde**, isim/URL/e-posta kararlarından sonra:

```powershell
git add --dry-run .
git add .
git update-index --chmod=+x Forge-1.12.2/gradlew
git diff --cached --stat
git diff --cached
git commit -m "Initial public source release"
git remote add origin https://github.com/RPM147/toms-trading-network-1.12.2-port.git
git push -u origin main
```

Origin zaten tanımlıysa remote ekleme komutunu tekrarlama. GitHub kimlik doğrulamasını güvenli giriş
yöntemiyle yap; tokenı URL'ye veya dosyaya yazma. AI tarafından oluşturulan commit
yapılırsa ilgili ortak-yazarlık bilgisini gerçeğe uygun ekle; tüm upstream kodunu
ilk commit'i yapan kişinin eseri gibi sunma.

## Paket ve platformlar

Başlangıç için Beta etiketi uygundur; kullanıcı testi, otomatik test ve genel
uyumluluk iddiasını ayır. GitHub Releases ve platformlara normal reobfuscated
JAR yüklenir; kaynak kod klasörüne mod JAR'ı commit edilmez. Host ve istemciler
aynı exact sürümü kullanmalıdır. Kayıt formatı/dünya yedeği uyarısını açıklamada tut.

CurseForge'a **Minecraft / Mods / Forge / 1.12.2**, MIT lisansı ve upstream atfıyla
başvur. İngilizce açıklama, bu reponun Source/Issues bağlantıları ve gerçek ekran
görüntüleri kullanılmalı. Modpack ZIP'i veya başka modların JAR'ları yüklenmemeli.
Platform onayı, bu klasörün hazırlanmış olmasından çıkarılamaz.

Modrinth için önce moderasyondan uygunluk görüşü al: port katkısında önemli ölçüde
AI üretimi kod/metin bulunduğunu açıkla. AI beyanı tek başına kabul garantisi değildir;
forklara eklenen içeriğin niteliği ayrıca değerlendirilir. Gereken derivative/AI
content alanlarını doğru doldur; upstream'in AI kullandığını iddia etme. AI üretimi
ikon kullanma. Kuralları başvuru gününde yeniden kontrol et.

Resmi kaynaklar:

- [GitHub yerel kod yükleme](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)
- [CurseForge proje oluşturma](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project)
- [Modrinth AI politikası](https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai)
- [Modrinth içerik beyanları](https://support.modrinth.com/en/articles/16567675-content-disclosures)

Bu belge CurseForge/Modrinth onayı veya bu platformlara yükleme yapıldığı anlamına gelmez.
