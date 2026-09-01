# Apple TV Remote

Ứng dụng Android điều khiển Apple TV qua giao thức **Companion link** của Apple.
Không cần server trung gian, không cần pyatv chạy kèm, không cần jailbreak —
toàn bộ phần bắt tay, mã hoá và giao thức được viết lại bằng Kotlin thuần.

Đã chạy thật với **Apple TV 4K** trên tvOS hiện hành.

## Tính năng

**Màn hình chọn thiết bị**

- Quét mDNS `_companion-link._tcp` và liệt kê Apple TV trong mạng nội bộ
- Ô nhập IP + cổng thủ công cho trường hợp mDNS bị chặn
- Nhớ thiết bị dùng lần cuối và tự kết nối lại khi mở app

**Màn hình điều khiển**

- Ô cảm ứng toàn màn hình hoạt động như bàn di của Siri Remote: vuốt gửi toạ độ
  tuyệt đối `0..1000`, chạm để chọn, giữ để mở menu ngữ cảnh
- Thanh dưới cùng: **Quay lại · Home · Đang chạy · Phát/Dừng · Tắt tiếng**, mỗi nút
  đều có phản hồi rung. Giữ **Home** để mở Control Center, còn **Đang chạy** gửi
  hai lần nhấn Home để bật App Switcher
- **Phím âm lượng vật lý** của điện thoại điều khiển âm lượng Apple TV kèm một nhịp
  rung nhẹ; sự kiện bị chặn lại nên không làm đổi âm lượng của máy
- Nút menu ở góc phải mở **modal danh sách ứng dụng** dạng lưới 5 cột, chọn app
  là mở luôn trên TV
- Icon app lấy từ iTunes lookup theo bundle ID, cache trên đĩa nên chỉ tải một lần;
  các app sẵn có của tvOS (App Store, Arcade, Cài đặt, Máy tính, Nhạc, TV, Tìm kiếm)
  dùng icon vector cố định vì không có trên App Store
- **Nhập văn bản** cho ô tìm kiếm của tvOS: gõ trên bàn phím điện thoại rồi gửi sang TV,
  có tuỳ chọn xoá sạch nội dung cũ trước khi gửi
- Ghép nối một lần bằng mã PIN hiện trên TV, credentials lưu riêng theo từng thiết bị

## Cài đặt

Tải APK ở mục [Releases](../../releases), hoặc tự build:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew :app:assembleRelease
```

APK nằm ở `app/build/outputs/apk/release/app-release.apk`. Yêu cầu Android 8.0 (API 26)
trở lên, và điện thoại phải cùng mạng Wi-Fi với Apple TV.

Chạy test giao thức:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew :app:testDebugUnitTest
```

## Cấu trúc

```
proto/Opack.kt              Định dạng nhị phân OPACK của Apple, có bảng tham chiếu đối tượng
proto/Tlv8.kt               TLV8 của HomeKit
proto/Srp.kt                SRP-6a 3072-bit / SHA-512, username "Pair-Setup"
proto/Crypto.kt             HKDF-SHA512, ChaCha20-Poly1305, Ed25519, X25519 (BouncyCastle)
proto/BinaryPlist.kt        Đọc/ghi binary plist, tương thích byte với plistlib của Python
proto/RtiPayloads.kt        Gói NSKeyedArchiver cho dịch vụ nhập văn bản RTI của tvOS
net/CompanionConnection.kt  Khung TCP 4 byte header và lớp mã hoá
net/CompanionClient.kt      Pair-setup, pair-verify, phiên làm việc và các lệnh điều khiển
net/Discovery.kt            Quét mDNS bằng NsdManager
net/ArtworkLoader.kt        Tra icon app qua iTunes lookup, cache RAM + đĩa
data/                       Lưu credentials và thiết bị gần nhất
ui/                         Compose: màn hình chọn thiết bị, màn hình điều khiển
```

## Luồng giao thức

1. Quét `_companion-link._tcp` để lấy IP và cổng
2. Chưa có credentials: `PS_Start` khiến Apple TV hiện mã 4 số, nhập mã rồi trao đổi
   SRP `M1`/`M5`, nhận về `ltpk` và định danh thiết bị
3. Đã có credentials: `PV_Start` / `PV_Next` dùng X25519 và Ed25519, sau đó bật
   ChaCha20-Poly1305 cho toàn bộ khung tiếp theo
4. `_systemInfo` → `_touchStart` → `_sessionStart` → `_tiStart`, rồi gửi `_hidC` cho nút
   bấm, `_hidT` cho bàn di và `_tiC` cho văn bản

## Kiểm chứng

Phần giao thức được đối chiếu **byte-for-byte** với pyatv trong
[`ProtocolTest.kt`](app/src/test/java/dev/duongvan/atvremote/ProtocolTest.kt):
sáu khung OPACK thật (kể cả trường hợp bảng tham chiếu trùng lặp), cùng các giá
trị SRP `A`, `M1`, `K`, `M2`.

Các gói NSKeyedArchiver dùng cho bàn phím cũng được so khớp byte với đầu ra của
`plistlib` trong [`PlistTest.kt`](app/src/test/java/dev/duongvan/atvremote/PlistTest.kt),
cả trường hợp văn bản tiếng Việt có dấu (chuỗi UTF-16).

## Hạn chế đã biết

- **Nhập văn bản** chỉ chạy khi TV đang mở sẵn một ô nhập; nếu không, app báo
  "Apple TV chưa mở ô nhập văn bản nào".
- **Tắt tiếng** dùng lệnh media control `SetVolume 0`. Nếu Apple TV điều khiển âm
  lượng của TV qua HDMI-CEC thay vì tự quản lý, lệnh này sẽ báo lỗi — dùng phím
  âm lượng vật lý thay thế.
- Companion không trả về ảnh icon, nên icon được tra từ App Store theo bundle ID.
  App sẵn có của tvOS dùng icon vector cố định; app cài tay không có trên store sẽ
  hiện chữ viết tắt.
- Chưa hỗ trợ Siri.
- Cổng Companion do thiết bị cấp động, nên khi nhập thủ công cần cả IP lẫn cổng.
- APK trong Releases được ký bằng debug key, đủ để cài trực tiếp nhưng không phát
  hành lên Play Store được.

## Giấy phép

[MIT](LICENSE).

Logic giao thức tham chiếu từ [pyatv](https://github.com/postlund/pyatv) của
Pierre Ståhl, cũng theo giấy phép MIT.
