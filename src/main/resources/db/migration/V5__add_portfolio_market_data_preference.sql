ALTER TABLE portfolios
    ADD COLUMN price_market_data_provider VARCHAR(50) NOT NULL DEFAULT 'TWELVE_DATA',
    ADD COLUMN candle_market_data_provider VARCHAR(50) NOT NULL DEFAULT 'TWELVE_DATA',
    ADD COLUMN asset_reference_market_data_provider VARCHAR(50) NOT NULL DEFAULT 'TWELVE_DATA';
