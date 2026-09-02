import {useEffect, useId, useState} from "react";
import {searchAssetListings} from "../../api/assetListingApi";
import type {AssetListing} from "../../types/assetListing";
import type {Market} from "../../types/tradeTransaction";

type Props = {
    market: Market;
    ticker: string;
    onTickerChange: (ticker: string) => void;
};

const SEARCH_DELAY_MS = 250;

type SearchResult = {
    key: string;
    results: AssetListing[];
    errorMessage: string | null;
};

export default function AssetSearchInput({market, ticker, onTickerChange}: Props) {
    const [searchResult, setSearchResult] = useState<SearchResult>({
        key: "",
        results: [],
        errorMessage: null,
    });
    const [isSearchOpen, setIsSearchOpen] = useState(false);
    const resultsId = useId();
    const query = ticker.trim();
    const searchKey = `${market}:${query}`;

    useEffect(() => {
        let isCurrentRequest = true;

        if (query.length === 0) return;

        const timer = window.setTimeout(() => {
            void searchAssetListings(market, query)
                .then((data) => {
                    if (!isCurrentRequest) return;

                    setSearchResult({key: searchKey, results: data, errorMessage: null});
                })
                .catch((reason: unknown) => {
                    if (!isCurrentRequest) return;

                    setSearchResult({
                        key: searchKey,
                        results: [],
                        errorMessage: reason instanceof Error
                            ? reason.message
                            : "종목 검색 결과를 불러오지 못했습니다.",
                    });
                });
        }, SEARCH_DELAY_MS);

        return () => {
            isCurrentRequest = false;
            window.clearTimeout(timer);
        };
    }, [market, query, searchKey]);

    const selectAsset = (asset: AssetListing) => {
        onTickerChange(asset.ticker);
        setIsSearchOpen(false);
    };

    const showResults = query.length > 0 && isSearchOpen;
    const isCurrentResult = searchResult.key === searchKey;
    const results = isCurrentResult ? searchResult.results : [];
    const errorMessage = isCurrentResult ? searchResult.errorMessage : null;
    const isSearching = query.length > 0 && !isCurrentResult;

    return <div className="asset-search">
        <label>종목 검색<input value={ticker} onChange={(event) => {
            onTickerChange(event.target.value.toUpperCase());
            setIsSearchOpen(true);
        }} onFocus={() => setIsSearchOpen(true)} onKeyDown={(event) => {
            if (event.key === "Escape") setIsSearchOpen(false);
        }} placeholder="티커 또는 종목명을 입력" maxLength={32} autoComplete="off" aria-controls={resultsId} aria-expanded={showResults && results.length > 0}/></label>
        {showResults && isSearching ? <p className="search-message" aria-live="polite">종목을 검색하는 중입니다.</p> : null}
        {showResults && errorMessage ? <p className="search-message error" role="alert">{errorMessage}</p> : null}
        {showResults && results.length > 0 ? <ul id={resultsId} className="asset-search-results" aria-label="종목 검색 결과">{results.map((asset) => <li key={`${asset.market}-${asset.ticker}`}><button type="button" onClick={() => selectAsset(asset)}><strong>{asset.ticker}</strong><span>{asset.displayName}</span></button></li>)}</ul> : null}
        {showResults && !isSearching && results.length === 0 && !errorMessage ? <p className="search-message" aria-live="polite">등록된 종목이 없으면 입력한 코드로 직접 등록할 수 있습니다.</p> : null}
    </div>;
}
