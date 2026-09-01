import {useEffect, useState} from "react";
import {searchAssetListings} from "../../api/assetListingApi";
import type {AssetListing} from "../../types/assetListing";
import type {Market} from "../../types/tradeTransaction";

type Props = {
    market: Market
    ticker: string
    onTickerChange: (ticker: string) => void
}

export default function AssetSearchInput({market, ticker, onTickerChange}: Props) {
    const [results, setResults] = useState<AssetListing[]>([]);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const query = ticker.trim();

    useEffect(() => {
        if (query.length === 0) return;

        void searchAssetListings(market, query)
            .then((data) => {
                setResults(data);
                setErrorMessage(null);
            })
            .catch((reason: unknown) => {
                setResults([]);
                setErrorMessage(reason instanceof Error ? reason.message : "종목 검색 결과를 불러오지 못했습니다.");
            });
    }, [market, query]);

    return <div className="asset-search">
        <label>종목 검색<input value={ticker} onChange={(event) => onTickerChange(event.target.value.toUpperCase())} placeholder="티커 또는 종목명을 입력" maxLength={32} autoComplete="off"/></label>
        {query.length > 0 && errorMessage ? <p className="search-message error" role="alert">{errorMessage}</p> : null}
        {query.length > 0 && results.length > 0 ? <ul className="asset-search-results">{results.map((asset) => <li key={asset.id}><button type="button" onClick={() => onTickerChange(asset.ticker)}><strong>{asset.ticker}</strong><span>{asset.displayName}</span></button></li>)}</ul> : null}
        {query.length > 0 && results.length === 0 && !errorMessage ? <p className="search-message">등록된 종목이 없으면 입력한 코드로 직접 등록할 수 있습니다.</p> : null}
    </div>;
}
