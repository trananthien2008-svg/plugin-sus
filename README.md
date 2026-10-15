# SusGUI

Plugin cho **Folia / Paper 1.20+** hiển thị GUI những người chơi đang bị **GrimAC** nghi vấn gian lận.

## Tính năng

- Lệnh `/sus` mở GUI 4 hàng (36 ô), **chỉ OP** mới mở được (console và người không phải OP bị từ chối).
- Mỗi ô là đầu của một người chơi bị Grim flag, kèm:
  - Ping (xanh/vàng/đỏ theo độ trễ), phiên bản client, IP
  - Tổng số lần bị flag và các check bị flag nhiều nhất (Reach, Simulation, ...)
  - Thời điểm và chi tiết (verbose) của lần flag gần nhất
- Chuột trái vào đầu người chơi: **dịch chuyển tới người đó**.
- Chuột phải: xoá người đó khỏi danh sách (khi đã xác nhận là flag nhầm).
- GUI tự cập nhật khi đang mở. Người chơi tự biến khỏi danh sách sau `expire-minutes` phút không bị flag thêm hoặc khi thoát server.
- Tương thích Folia: dùng `teleportAsync`, EntityScheduler, GlobalRegion/Async scheduler; `folia-supported: true`.

## Build

Cần JDK 17+ và Maven:

```
mvn clean package
```

File jar nằm ở `target/SusGUI.jar`, bỏ vào thư mục `plugins/` của server.

## Yêu cầu

- Folia hoặc Paper 1.20+ (không chạy trên Spigot thuần vì dùng scheduler của Paper/Folia)
- GrimAC (bản có API `FlagEvent`; plugin tự thử cả API mới `EventBus` và API Bukkit event cũ)
- ViaVersion (tuỳ chọn, dùng để hiện phiên bản client nếu Grim không trả về được)

## Cấu hình (`config.yml`)

| Key | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `expire-minutes` | 10 | Số phút không bị flag thì tự xoá khỏi danh sách |
| `min-flags` | 1 | Số lần flag tối thiểu để hiện trong GUI |
| `refresh-ticks` | 40 | Chu kỳ tự cập nhật GUI (20 tick = 1 giây) |
| `show-ip` | true | Hiện IP trong GUI |
| `show-verbose` | true | Hiện chi tiết lần flag gần nhất |
| `remove-on-quit` | true | Xoá khỏi danh sách khi người chơi thoát |
| `debug` | false | In log mỗi khi nhận flag từ Grim (để kiểm tra kết nối) |

## Kiểm tra hoạt động

1. Khởi động server, console phải có dòng `Đã kết nối GrimAC (...)`.
2. Bật `debug: true`, khi có người bị Grim flag console sẽ in `[debug] <tên> flag <check> ...`.
3. Gõ `/sus` bằng tài khoản OP.

Nếu console báo `Không kết nối được GrimAC`, gửi cho mình phiên bản GrimAC đang dùng để chỉnh lại phần hook (`GrimHook.java`).
