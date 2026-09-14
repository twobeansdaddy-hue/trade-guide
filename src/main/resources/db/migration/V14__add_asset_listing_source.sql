ALTER TABLE asset_listings ADD COLUMN source VARCHAR(255);

UPDATE asset_listings SET source = 'EXTERNAL_SEARCH' WHERE source IS NULL;

ALTER TABLE asset_listings ALTER COLUMN source SET NOT NULL;
