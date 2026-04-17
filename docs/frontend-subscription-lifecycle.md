# Subscription lifecycle — Frontend & DB

## Hybrid behaviour

1. **Lazy** — `GET /api/membership/current` และ `GET /api/subscriptions/{id}` ยังเรียก logic ย้าย **downgrade ที่ถึงเวลา** (`applyPendingPlanChangesIfDue`)
2. **Cron** — ตาม `scheduler.subscription.cron` (ค่าเริ่มต้นทุกชั่วโมง) ประมวลผล subscription ที่ `ACTIVE` และ `current_period_end <= now()`:
   - **มี** `autoRenew` → ย้าย scheduled **downgrade** ก่อน (ถ้าถึงเวลา) → **ตัดเงิน Omise** (ใช้ `omise_customer_id` + default card) → สำเร็จแล้วขยาย `current_period_*` + `billing_records`
   - **ไม่** `autoRenew` → พยายาม **สลับไปใช้ bank ของแผนที่ราคาสูงสุดที่มีวันคงเหลือ** ก่อน; ถ้าไม่มีทางเลือกที่ใช้ bank ได้ → ตั้ง `status = EXPIRED` (ไม่ตัดเงิน)
   - ตัดเงินไม่สำเร็จ → `EXPIRED`
   - **หมายเหตุ:** ถ้า `current_period_end` ค้างในอดีตนาน ระบบจะยึดจุดเริ่มรอบใหม่จากเวลาปัจจุบัน (ไม่บวกทีละรอบจากวันหมดเก่าเรื่อย ๆ) เพื่อไม่ให้ตัดเงินซ้ำทุก tick ของ cron

## API ใหม่ (ยกเลิก / เปิดกลับ)

| Method | Path | ความหมาย |
|--------|------|-----------|
| `POST` | `/api/subscriptions/cancel` | หยุดต่ออายุ — `auto_renew=false`, `cancel_at=current_period_end`, `cancelled_at=now` (ใช้แพ็กจนสิ้นรอบ) |
| `POST` | `/api/subscriptions/resume` | เปิดกลับ `auto_renew` ก่อนสิ้นรอบ (`cancel_at` / `cancelled_at` = null) |

Header: `Authorization: Bearer <JWT>`  
Response: `204 No Content`

## ข้อมูลใน `GET /api/membership/current`

- `autoRenew`, `cancelAt`, `cancelledAt`
- `pendingPlan` / `scheduledPlanChangeAt` — downgrade ที่รอสิ้นรอบ
- `currentPlanBankedDays` — วันใน bank ของแผนปัจจุบัน
- `bankedPlans` — รายการ bank ต่อแผน (ดูรายละเอียดฟิลด์ใน `docs/frontend-plan-change.md`)

## DB

- ตาราง `subscriptions`: `auto_renew`, `cancel_at`, `cancelled_at`, `current_period_*`, `pending_plan_id`, `scheduled_plan_change_at`
- ตาราง **`subscription_day_banks`**: เก็บวันคงเหลือต่อ `(user_id, plan_id)` — DDL ใน `docs/subscription-day-bank-migration-notes.md`

## การตั้งค่า (Backend)

| Property | ค่าเริ่มต้น | ความหมาย |
|----------|-------------|-----------|
| `scheduler.subscription.enabled` | `true` | ปิด `false` ใน test หรือเวลาไม่ต้องการ cron |
| `scheduler.subscription.cron` | `0 0 * * * *` | รอบรัน job (cron 6 ฟิลด์: วินาที …) |
| `subscription.billing.period` | `P30D` | ความยาวรอบบิลต่อแพ็ก (default 30 วัน) ใช้คำนวณ `current_period_end` |

## Prompt สำหรับทีม Frontend (คัดลอกได้)

```
ระบบ subscription ใช้แบบ hybrid:
- ต่ออายุ/หมดอายุ/ย้าย downgrade ที่ถึงเวลา จะทำงานทั้งตอนโหลด membership (GET /api/membership/current) และตามรอบ cron จาก backend
- เมื่อสิ้นรอบและปิด auto-renew: backend อาจสลับไปใช้วันจาก bank (แผนราคาสูงสุดที่มี bank ก่อน) ก่อนจะหมดอายุ — แสดง bankedPlans / currentPlanBankedDays ให้สอดคล้อง
- ผู้ใช้ยกเลิกแพ็ก: POST /api/subscriptions/cancel
  - ใช้สิทธิ์แพ็กจนสิ้นรอบบิลปัจจุบัน (ดู cancelAt จาก GET membership)
  - autoRenew จะเป็น false จะไม่หักเงินรอบถัดไป
- เปิดกลับก่อนสิ้นรอบ: POST /api/subscriptions/resume
- แสดงใน UI: autoRenew, cancelAt, cancelledAt, pendingPlan, scheduledPlanChangeAt, currentPlanBankedDays, bankedPlans
- หลัง cancel ยังไม่ควรซ่อนบัตร/แพ็กทันที — แสดงข้อความ "ใช้ได้ถึง {cancelAt}" หรือ currentPeriodEnd
```
