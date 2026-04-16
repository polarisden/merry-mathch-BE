# Subscription lifecycle — Frontend & DB

## Hybrid behaviour

1. **Lazy** — `GET /api/membership/current` และ `GET /api/subscriptions/{id}` ยังเรียก `applyPendingPlanChangesIfDue` (ย้าย downgrade ที่ถึงเวลา)
2. **Cron** — ทุก **1 ชั่วโมง** (ปรับได้ใน `scheduler.subscription.cron`) ประมวลผล subscription ที่ `ACTIVE` และ `current_period_end <= now()`:
   - **ไม่** `autoRenew` → ตั้ง `status = EXPIRED` (ไม่ตัดเงิน)
  - **มี** `autoRenew` → ย้าย scheduled **downgrade** ก่อน (ถ้าถึงเวลา) → **ตัดเงิน Omise** (ใช้ `omise_customer_id` + default card ของ customer) → สำเร็จแล้วค่อยขยาย `current_period_start` / `current_period_end` + `billing_records`
   - ตัดเงินไม่สำเร็จ → `EXPIRED`
   - **หมายเหตุ:** ถ้า `current_period_end` ค้างในอดีตนาน ระบบจะยึดจุดเริ่มรอบใหม่จากเวลาปัจจุบัน (ไม่บวกทีละรอบจากวันหมดเก่าเรื่อยๆ) เพื่อไม่ให้ตัดเงินซ้ำทุก tick ของ cron

## API ใหม่ (ยกเลิก / เปิดกลับ)

| Method | Path | ความหมาย |
|--------|------|-----------|
| `POST` | `/api/subscriptions/cancel` | หยุดต่ออายุ — `auto_renew=false`, `cancel_at=current_period_end`, `cancelled_at=now` (ใช้แพ็กจนสิ้นรอบ) |
| `POST` | `/api/subscriptions/resume` | เปิดกลับ `auto_renew` ก่อนสิ้นรอบ (`cancel_at` / `cancelled_at` = null) |

Header: `Authorization: Bearer <JWT>`  
Response: `204 No Content`

## ข้อมูลใน `GET /api/membership/current` (มีอยู่แล้ว)

- `autoRenew` — จะต่ออายุอัตโนมัติหรือไม่  
- `cancelAt` — สิทธิ์ใช้งานถึงเมื่อไหร่ (เมื่อผู้ใช้กด cancel = สิ้นรอบบิล)  
- `cancelledAt` — เวลาที่กด cancel  
- `pendingPlan` / `scheduledPlanChangeAt` — downgrade ที่รอสิ้นรอบ  

## DB

ไม่มีคอลัมน์ใหม่สำหรับฟีเจอร์นี้ — ใช้ `auto_renew`, `cancel_at`, `cancelled_at`, `current_period_*`, `pending_plan_id`, `scheduled_plan_change_at` ที่มีอยู่

## การตั้งค่า (Backend)

| Property | ค่าเริ่มต้น | ความหมาย |
|----------|-------------|-----------|
| `scheduler.subscription.enabled` | `true` | ปิด `false` ใน test หรือเวลาไม่ต้องการ cron |
| `scheduler.subscription.cron` | `0 0 * * * *` | รอบรัน job (cron 6 วินาที) |
| `subscription.billing.period` | `P30D` | ความยาวรอบบิลต่อแพ็ก (default 30 วัน) ใช้คำนวณ `current_period_end` |

## Prompt สำหรับทีม Frontend (คัดลอกได้)

```
ระบบ subscription ใช้แบบ hybrid:
- ต่ออายุ/หมดอายุ/ย้าย downgrade ที่ถึงเวลา จะทำงานทั้งตอนโหลด membership (GET /api/membership/current) และตามรอบ cron จาก backend
- ผู้ใช้ยกเลิกแพ็ก: POST /api/subscriptions/cancel
  - ใช้สิทธิ์แพ็กจนสิ้นรอบบิลปัจจุบัน (ดู cancelAt จาก GET membership)
  - autoRenew จะเป็น false จะไม่หักเงินรอบถัดไป
- เปิดกลับก่อนสิ้นรอบ: POST /api/subscriptions/resume
- แสดงใน UI: autoRenew, cancelAt, cancelledAt, pendingPlan, scheduledPlanChangeAt
- หลัง cancel ยังไม่ควรซ่อนบัตร/แพ็กทันที — แสดงข้อความ "ใช้ได้ถึง {cancelAt}" หรือ currentPeriodEnd
```
