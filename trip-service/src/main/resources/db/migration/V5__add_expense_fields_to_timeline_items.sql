-- Add expense tracking fields to timeline_items
-- Supports recording expenses (e.g., meals, tickets) along with notes

ALTER TABLE trip.timeline_items
ADD COLUMN amount DECIMAL(15, 2),
ADD COLUMN currency VARCHAR(3);

-- Add comment for documentation
COMMENT ON COLUMN trip.timeline_items.amount IS 'Expense amount (nullable, used primarily with note type)';
COMMENT ON COLUMN trip.timeline_items.currency IS 'ISO 4217 currency code (e.g., KRW, USD, EUR)';
