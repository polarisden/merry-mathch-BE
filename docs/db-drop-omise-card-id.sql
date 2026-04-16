-- Drop omise_card_id from subscriptions (no longer persisted).
-- Safe to run once; column drop is irreversible.

ALTER TABLE public.subscriptions
  DROP COLUMN IF EXISTS omise_card_id;

