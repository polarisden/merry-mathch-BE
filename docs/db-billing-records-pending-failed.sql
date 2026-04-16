-- Enable recording pending/failed billing attempts.
-- Notes:
-- - This assumes you already created billing_status enum and billing_records table.
-- - Some statements may require adjusting depending on existing constraints/data.

-- 1) Add PENDING to billing_status enum (Postgres)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_enum e
    JOIN pg_type t ON t.oid = e.enumtypid
    WHERE t.typname = 'billing_status' AND e.enumlabel = 'PENDING'
  ) THEN
    ALTER TYPE billing_status ADD VALUE 'PENDING';
  END IF;
END $$;

-- 2) Allow billing_records without subscription (first-time checkout may fail/pending)
ALTER TABLE public.billing_records
  ALTER COLUMN subscription_id DROP NOT NULL;

-- 3) Add user_id for direct ownership (used for pending/failed before subscription exists)
ALTER TABLE public.billing_records
  ADD COLUMN IF NOT EXISTS user_id uuid;

-- Backfill user_id from existing subscription join
UPDATE public.billing_records br
SET user_id = s.user_id
FROM public.subscriptions s
WHERE br.subscription_id = s.id
  AND br.user_id IS NULL;

-- Enforce user_id not null (after backfill)
ALTER TABLE public.billing_records
  ALTER COLUMN user_id SET NOT NULL;

-- Add FK to users
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints tc
    WHERE tc.table_schema = 'public'
      AND tc.table_name = 'billing_records'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND tc.constraint_name = 'billing_records_user_id_fkey'
  ) THEN
    ALTER TABLE public.billing_records
      ADD CONSTRAINT billing_records_user_id_fkey
      FOREIGN KEY (user_id) REFERENCES public.users(id);
  END IF;
END $$;

