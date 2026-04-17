# Subscription day bank — schema & behaviour

ระบบเก็บวันคงเหลือแยกตาม **แผน** (`user_id` + `plan_id`) ไม่โอนวันข้ามแผน

## Database

ตาราง `subscription_day_banks`:

- คีย์ไม่ซ้ำ: `(user_id, plan_id)`
- เก็บ `remaining_days` ต่อคู่ user–plan

DDL อ้างอิง (รันใน Supabase / env ใหม่ หรือให้ Flyway รันเทียบเท่าได้):

```sql
CREATE TABLE IF NOT EXISTS public.subscription_day_banks (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  plan_id uuid NOT NULL REFERENCES public.plans(id) ON DELETE CASCADE,
  remaining_days integer NOT NULL DEFAULT 0,
  updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT subscription_day_banks_pkey PRIMARY KEY (id),
  CONSTRAINT subscription_day_banks_user_plan_unique UNIQUE (user_id, plan_id)
);
```

### Flyway vs รัน SQL เอง

- ถ้าทีม **รัน DDL ใน Supabase แล้ว** ไม่จำเป็นต้องมีไฟล์ใน `src/main/resources/db/migration/` — โค้ดใช้ตารางนี้โดยตรง
- ถ้าต้องการ reproducible deploy ผ่าน Flyway ให้เพิ่ม migration เทียบเท่ากับ DDL ด้านบน

## พฤติกรรม (สรุป)

- **Upgrade:** เรียกเก็บ **ราคาเต็ม** ของแผนปลายทาง (Omise) เมื่อสำเร็จ — ย้ายแผนทันที, เก็บวันคงเหลือของแผนเดิมเข้า bank ของแผนนั้น, ใช้ bank ของแผนปลายทาง (ถ้ามี) ต่อยอดรอบบิล
- **Downgrade:** **ไม่เรียกเก็บ** ตอนกด — ตั้ง **pending** จนกว่าจะถึง `current_period_end` แล้วค่อยสลับแผนและใช้ bank ของแผนปลายทางตาม logic ใน service
- **หลาย bank / ลำดับ:** เวลาเลือกอัตโนมัติ (เช่น สิ้นรอบ + auto-renew ปิด) ระบบพิจารณาแผนที่มี bank โดยเรียงจาก **ราคาแผนสูงสุดก่อน** (`priceSatang`)

## Frontend / API

- Preview: `PlanChangePreviewResponse` — `chargeAmountSatang`, `immediateEffective`, `bankedDaysFromCurrentPlan`, `bankedDaysAvailableOnTargetPlan` (ไม่ใช้ proration fields เดิม)
- Membership: `SubscriptionDetailDto` — `currentPlanBankedDays`, `bankedPlans` (`planId`, `planName`, `planPriceSatang`, `remainingDays`), ร่วมกับ `pendingPlan` / `scheduledPlanChangeAt` ตามเดิม
