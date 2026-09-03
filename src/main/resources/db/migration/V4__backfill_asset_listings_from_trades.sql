INSERT INTO asset_listings (market, ticker, display_name, listing_status)
SELECT DISTINCT trade.market, trade.ticker, trade.ticker, 'ACTIVE'
FROM trade_transactions trade
WHERE NOT EXISTS (
    SELECT 1
    FROM asset_listings listing
    WHERE listing.market = trade.market
      AND listing.ticker = trade.ticker
);
