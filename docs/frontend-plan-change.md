# Plan upgrade / downgrade — สเปกสำหรับ Frontend

## พฤติกรรม (Backend)

| การกระทำ | การเงิน | เมื่อมีผล |
|----------|---------|-----------|
| **Upgrade** (แพงขึ้น) | เรียกเก็บ **ราคาเต็ม** ของแผนปลายทาง (สตางค์ตาม `chargeAmountSatang` ใน preview) | **ทันที** หลังจ่ายสำเร็จ — วันคงเหลือของแผนเดิมถูก **bank** ต่อแผนเดิม; ถ้ามี bank ของแผนปลายทางจะถูกนำไปต่อรอบบิล |
| **Downgrade** (ถูกลง) | **ไม่เรียกเก็บ** ตอนกด | **สิ้นรอบบิลปัจจุบัน** (`currentPeriodEnd`) — เก็บเป็น pending จนกว่าจะถึงเวลา |

- ก่อนแสดง membership ระบบจะย้ายแผน downgrade ที่ถึงเวลาเมื่อเรียก `GET /api/membership/current` หรือ `GET /api/subscriptions/{id}` (lazy apply)

## API

### 1. Preview

`POST /api/subscriptions/plan-change/preview`  
Header: `Authorization: Bearer <JWT>`  
Body:

```json
{ "planId": "<uuid แผนปลายทาง>" }
```

Response 200 — `PlanChangePreviewResponse`:

| Field | ความหมาย |
|-------|-----------|
| `changeType` | `SAME` \| `UPGRADE` \| `DOWNGRADE` |
| `currentPlanId` / `currentPlanName` | แผนปัจจุบัน |
| `targetPlanId` / `targetPlanName` | แผนที่เลือก |
| `chargeAmountSatang` | ยอดที่จะเรียกเก็บเมื่อยืนยัน upgrade (ราคาเต็มแผนปลายทาง); `null` ถ้าไม่เกี่ยว |
| `immediateEffective` | `true` เมื่อเปลี่ยนแผนทันทีหลังจ่าย (upgrade) |
| `bankedDaysFromCurrentPlan` | วันที่จะถูกเก็บจากแผนปัจจุบันถ้ายืนยัน |
| `bankedDaysAvailableOnTargetPlan` | วันที่สะสมไว้ของแผนปลายทาง (bank ของ plan นั้น) |
| `description` | ข้อความอธิบายสั้น ๆ |

> ไม่ใช้ `proratedAmountSatang` / `scheduledEffectiveAt` — ใช้ฟิลด์ด้านบนแทน

### 2. Schedule downgrade

`POST /api/subscriptions/plan-change/downgrade`  
Body: เหมือน preview (`planId`)  
Response: `202 Accepted` (ไม่มี body)

### 3. Confirm upgrade (ชำระเงิน)

`POST /api/subscriptions/plan-change/upgrade`  
Body:

```json
{
  "planId": "<uuid>",
  "omiseToken": "tokn_test_..."
}
```

Response: เหมือน `SubscriptionCheckoutResponse` (มี `status` paid/pending, `chargeId`, `paymentCard` เมื่อ paid ทันที)

- ถ้า `pending` ให้รอ webhook + poll membership เหมือน flow สมัครใหม่

## Membership DTO เพิ่ม

`GET /api/membership/current` — ใน `SubscriptionDetailDto`:

| Field | ความหมาย |
|-------|-----------|
| `pendingPlan` | แผนที่จะสลับ (ถ้ามีการ schedule downgrade) — โครงสร้างเดียวกับ `plan` |
| `scheduledPlanChangeAt` | เวลาที่จะสลับเป็น `pendingPlan` |
| `currentPlanBankedDays` | จำนวนวันใน bank **ของแผนปัจจุบัน** |
| `bankedPlans` | รายการ bank ทุกแผนที่มีวัน (รายการละ `planId`, `planName`, `planPriceSatang`, `remainingDays`) |

## SQL (DBA / Supabase) — คอลัมน์ pending downgrade

รันครั้งเดียวถ้ายังไม่มีคอลัมน์:

```sql
ALTER TABLE public.subscriptions
  ADD COLUMN IF NOT EXISTS pending_plan_id uuid NULL REFERENCES public.plans(id);
ALTER TABLE public.subscriptions
  ADD COLUMN IF NOT EXISTS scheduled_plan_change_at timestamp NULL;
```

ตาราง `subscription_day_banks` — ดู DDL ใน `docs/subscription-day-bank-migration-notes.md`

## Prompt สำหรับทีม Frontend (คัดลอกได้)

```
เราต้องการรองรับเปลี่ยนแผน subscription:

1) หน้าเลือกแผน: เรียก POST /api/subscriptions/plan-change/preview ด้วย planId ปลายทาง
   - ถ้า changeType === "SAME" แสดงว่าเป็นแผนเดียวกัน
   - ถ้า "UPGRADE" แสดงยอด chargeAmountSatang (แสดงเป็นบาท: หาร 100) คือราคาเต็มแผนปลายทาง — แสดง bankedDaysFromCurrentPlan / bankedDaysAvailableOnTargetPlan ตาม preview
     แล้วใช้ Omise.js สร้าง token แล้วเรียก POST .../upgrade ด้วย planId + omiseToken
   - ถ้า "DOWNGRADE" แสดงข้อความว่าจะเปลี่ยนเมื่อสิ้นรอบบิล และปุ่มยืนยันเรียก POST .../downgrade (ไม่ต้องใส่บัตร)

2) หลัง upgrade สำเร็จ หรือเมื่อโหลดหน้า account: เรียก GET /api/membership/current
   - ถ้ามี pendingPlan + scheduledPlanChangeAt ให้แสดงแบนเนอร์ "แผนจะเปลี่ยนเป็น ... วันที่ ..."
   - แสดง currentPlanBankedDays และรายการ bankedPlans ถ้าต้องการ UI รายละเอียด

3) จัดการ error จาก API (400) ตาม message ใน body

4) ใช้ token Omise ใหม่ทุกครั้งที่ upgrade (ไม่ reuse token)
```
