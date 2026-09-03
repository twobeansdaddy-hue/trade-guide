import {useEffect, useId, useRef, useState, type KeyboardEvent} from "react";
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

type HighlightedResult = {
    searchKey: string;
    index: number;
};

export default function AssetSearchInput({market, ticker, onTickerChange}: Props) {
    const [searchResult, setSearchResult] = useState<SearchResult>({
        key: "",
        results: [],
        errorMessage: null,
    });
    const [isSearchOpen, setIsSearchOpen] = useState(false);
    const [highlightedResult, setHighlightedResult] = useState<HighlightedResult>({
        searchKey: "",
        index: -1,
    });
    const resultListRef = useRef<HTMLUListElement>(null);
    const inputId = useId();
    const resultsId = useId();
    const feedbackId = useId();
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
        setHighlightedResult({searchKey, index: -1});
    };

    const showResults = query.length > 0 && isSearchOpen;
    const isCurrentResult = searchResult.key === searchKey;
    const results = isCurrentResult ? searchResult.results : [];
    const errorMessage = isCurrentResult ? searchResult.errorMessage : null;
    const isSearching = query.length > 0 && !isCurrentResult;
    const isResultListVisible = showResults && results.length > 0;
    const highlightedIndex = highlightedResult.searchKey === searchKey && isResultListVisible
        ? highlightedResult.index
        : -1;
    const highlightedOptionId = highlightedIndex >= 0
        ? `${resultsId}-option-${highlightedIndex}`
        : undefined;

    useEffect(() => {
        if (highlightedIndex < 0) return;

        const highlightedOption = resultListRef.current?.children[highlightedIndex];

        if (highlightedOption instanceof HTMLElement) {
            highlightedOption.scrollIntoView({block: "nearest"});
        }
    }, [highlightedIndex]);

    const closeResults = () => {
        setIsSearchOpen(false);
        setHighlightedResult({searchKey, index: -1});
    };

    const moveHighlight = (step: 1 | -1) => {
        setHighlightedResult((current) => {
            const previousIndex = current.searchKey === searchKey ? current.index : -1;
            const nextIndex = step === 1
                ? (previousIndex + 1) % results.length
                : (previousIndex <= 0 ? results.length : previousIndex) - 1;

            return {searchKey, index: nextIndex};
        });
    };

    const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
        if (event.key === "Escape") {
            if (showResults) event.preventDefault();

            closeResults();
            return;
        }

        if (event.key === "ArrowDown") {
            if (query.length === 0) return;

            event.preventDefault();

            if (!isSearchOpen) {
                setIsSearchOpen(true);
                return;
            }

            if (!isResultListVisible) return;

            moveHighlight(1);
            return;
        }

        if (event.key === "ArrowUp") {
            if (!isResultListVisible) return;

            event.preventDefault();
            moveHighlight(-1);
            return;
        }

        if (event.key === "Enter" && isResultListVisible) {
            event.preventDefault();

            if (highlightedIndex >= 0) {
                selectAsset(results[highlightedIndex]);
                return;
            }

            closeResults();
        }
    };

    return <div className="asset-search">
        <label htmlFor={inputId}>종목 검색</label>
        <div className="asset-search-input-wrap">
            <input id={inputId} value={ticker} onChange={(event) => {
                onTickerChange(event.target.value.toUpperCase());
                setIsSearchOpen(true);
            }} onFocus={() => setIsSearchOpen(true)} onKeyDown={handleKeyDown} placeholder="티커 또는 종목명을 입력" maxLength={32} autoComplete="off" role="combobox" aria-autocomplete="list" aria-haspopup="listbox" aria-controls={isResultListVisible ? resultsId : undefined} aria-describedby={showResults ? feedbackId : undefined} aria-activedescendant={highlightedOptionId} aria-expanded={isResultListVisible}/>
            {isResultListVisible ? <ul ref={resultListRef} id={resultsId} className="asset-search-results" role="listbox" aria-label="종목 검색 결과">{results.map((asset, index) => <li key={`${asset.market}-${asset.ticker}`} id={`${resultsId}-option-${index}`} role="option" aria-selected={highlightedIndex === index} onMouseDown={(event) => event.preventDefault()} onClick={() => selectAsset(asset)}><strong>{asset.ticker}</strong><span>{asset.displayName}</span></li>)}</ul> : null}
        </div>
        {showResults ? <div id={feedbackId} className="asset-search-feedback" aria-live="polite">
            {showResults && isSearching ? <p className="search-message">종목을 검색하는 중입니다.</p> : null}
            {showResults && errorMessage ? <p className="search-message error">{errorMessage}</p> : null}
            {showResults && !isSearching && results.length === 0 && !errorMessage ? <p className="search-message">등록된 종목이 없으면 입력한 코드로 직접 등록할 수 있습니다.</p> : null}
        </div> : null}
    </div>;
}
