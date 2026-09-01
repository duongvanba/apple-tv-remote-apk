# Apple TV Remote (Android)

Ứng dụng Android điều khiển Apple TV qua giao thức **Companion link** của Apple —
không cần server trung gian, không cần pyatv chạy kèm. Toàn bộ phần mã hoá và
giao thức được viết lại bằng Kotlin.

## Tính năng

| Màn hình | Nội dung |
|---|---|
| Chọn thiết bị | Quét mDNS `_companion-link._tcp`, hiện tên/model/IP; có ô nhập IP + cổng thủ công |
| Điều khiển | Ô cảm ứng vuốt toàn màn hình, thanh nút dưới cùng, hộp thoại nhập mã ghép nối khi thiết bị chưa pair |

- **Vuốt**: hai chế độ, đổi bằng nút trên thanh tiêu đề
  - `Trackpad` (mặc định): gửi toạ độ tuyệt đối 0–1000 theo ngón tay, giống bàn di của Siri Remote
  - `Hướng`: mỗi cú vuốt thành một lệnh Lên/Xuống/Trái/Phải
  - Nút đảo chiều dọc nếu Apple TV hiểu ngược trục Y
- **Chạm** để chọn, **giữ** để mở menu ngữ cảnh
- **Âm lượng bằng phím vật lý** của điện thoại (chặn không cho đổi âm lượng máy khi đang có phiên)
- **Thanh dưới cùng**: Home (giữ để mở App Switcher), Mở ứng dụng, Phát/Dừng, Tắt tiếng
- Nút Menu/Quay lại nằm ở góc phải thanh tiêu đề
- Ghép nối một lần, credentials lưu trong SharedPreferences theo từng thiết bị

## Build

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk` (ký bằng debug key, cài trực tiếp được).

Chạy test giao thức:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew :app:testDebugUnitTest
```

## Cấu trúc

```
proto/Opack.kt      Định dạng nhị phân OPACK của Apple (pack + unpack, có bảng tham chiếu)
proto/Tlv8.kt       TLV8 của HomeKit
proto/Srp.kt        SRP-6a 3072-bit / SHA-512, username "Pair-Setup"
proto/Crypto.kt     HKDF-SHA512, ChaCha20-Poly1305, Ed25519, X25519 (BouncyCastle)
net/CompanionConnection.kt  Khung TCP 4 byte header + lớp mã hoá
net/CompanionClient.kt      Pair-setup, pair-verify, phiên và các lệnh điều khiển
net/Discovery.kt    Quét mDNS bằng NsdManager
data/               Lưu credentials và thiết bị gần nhất
ui/                 Compose: màn hình thiết bị, màn hình điều khiển
```

## Luồng giao thức

1. `_companion-link._tcp` → IP + cổng
2. Chưa có credentials → `PS_Start` (Apple TV hiện mã 4 số) → nhập mã → SRP `M1`/`M5` →
   nhận `ltpk` + định danh thiết bị
3. Có credentials → `PV_Start`/`PV_Next` (X25519 + Ed25519) → bật ChaCha20-Poly1305
4. `_systemInfo` → `_touchStart` → `_sessionStart` → sẵn sàng nhận `_hidC` (nút) và `_hidT` (bàn di)

## Kiểm chứng

Phần giao thức được đối chiếu **byte-for-byte** với pyatv (MIT) trong
`app/src/test/.../ProtocolTest.kt`: 6 khung OPACK thật (kể cả trường hợp bảng
tham chiếu trùng lặp), và các giá trị SRP `A`, `M1`, `K`, `M2` khớp với phép
toán srptools mà pyatv dùng.

## Hạn chế đã biết

- Chưa chạy thử với Apple TV thật trong phiên này — mới xác minh ở mức giao thức.
- **Tắt tiếng** dùng lệnh media control `SetVolume 0`. Nếu Apple TV chỉnh âm lượng
  TV qua HDMI-CEC thay vì tự quản lý, lệnh này sẽ báo lỗi; dùng phím vật lý thay thế.
- Chiều trục Y của bàn di chưa kiểm chứng trên thiết bị thật — có nút đảo chiều sẵn.
- Chưa hỗ trợ nhập văn bản (bàn phím trên tvOS) và Siri.
- Cổng Companion do thiết bị cấp động, nên nhập thủ công cần cả IP lẫn cổng.

## Ghi công

Logic giao thức tham chiếu [pyatv](https://github.com/postlund/pyatv) (MIT License).
