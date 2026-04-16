# Plan upgrade / downgrade — สเปกสำหรับ Frontend

## พฤติกรรม (Backend)

| การกระทำ | การเงิน | เมื่อมีผล |
|----------|---------|-----------|
| **Upgrade** (แพงขึ้น) | เรียกเก็บ **แบบ prorated** ตามส่วนที่เหลือของรอบบิล (ขั้นต่ำ ~20 THB ถ้า prorate ต่ำกว่านั้นจะปัดขึ้น) | ทันทีหลังจ่ายสำเร็จ |
| **Downgrade** (ถูกลง) | **ไม่เรียกเก็บ** ตอนกด | ณ **สิ้นรอบบิลปัจจุบัน** (`currentPeriodEnd`) |

- ก่อนแสดง membership ระบบจะย้ายแผน downgrade อัตโนมัติเมื่อเวลาถึง (เมื่อเรียก `GET /api/membership/current` หรือ `GET /api/subscriptions/{id}`).

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
| `proratedAmountSatang` | ยอดที่จะเรียกเก็บ (upgrade) เป็นสตางค์; `null` ถ้าไม่เกี่ยว |
| `scheduledEffectiveAt` | วันที่ downgrade จะมีผล; `null` ถ้าไม่เกี่ยว |
| `description` | ข้อความอธิบายสั้น ๆ |

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

`GET /api/membership/current` — ใน `SubscriptionDetailDto` มีเพิ่ม:

| Field | ความหมาย |
|-------|-----------|
| `pendingPlan` | แผนที่จะสลับ (ถ้ามีการ schedule downgrade) — โครงสร้างเดียวกับ `plan` |
| `scheduledPlanChangeAt` | เวลาที่จะสลับเป็น `pendingPlan` |

## SQL (DBA / Supabase)

รันครั้งเดียวถ้ายังไม่มีคอลัมน์:

```sql
ALTER TABLE public.subscriptions
  ADD COLUMN IF NOT EXISTS pending_plan_id uuid NULL REFERENCES public.plans(id);
ALTER TABLE public.subscriptions
  ADD COLUMN IF NOT EXISTS scheduled_plan_change_at timestamp NULL;
```

## Prompt สำหรับทีม Frontend (คัดลอกได้)

```
เราต้องการรองรับเปลี่ยนแผน subscription:

1) หน้าเลือกแผน: เรียก POST /api/subscriptions/plan-change/preview ด้วย planId ปลายทาง
   - ถ้า changeType === "SAME" แสดงว่าเป็นแผนเดียวกัน
   - ถ้า "UPGRADE" แสดงยอด proratedAmountSatang (แสดงเป็นบาท: หาร 100) และปุ่มชำระด้วย Omise.js แล้วเรียก POST .../upgrade ด้วย planId + omiseToken
   - ถ้า "DOWNGRADE" แสดงข้อความว่าจะเปลี่ยนเมื่อสิ้นรอบบิล (scheduledEffectiveAt) และปุ่มยืนยันเรียก POST .../downgrade (ไม่ต้องใส่บัตร)

2) หลัง upgrade สำเร็จ หรือเมื่อโหลดหน้า account: เรียก GET /api/membership/current
   - ถ้ามี pendingPlan + scheduledPlanChangeAt ให้แสดงแบนเนอร์ "แผนจะเปลี่ยนเป็น ... วันที่ ..."

3) จัดการ error จาก API (400) ตาม message ใน body

4) ใช้ token Omise ใหม่ทุกครั้งที่ upgrade (ไม่ reuse token)
```
