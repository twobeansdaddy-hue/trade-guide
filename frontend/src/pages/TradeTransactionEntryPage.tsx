import {useState, type FormEvent} from "react";
import {useNavigate} from "react-router-dom";
import {createTradeTransaction} from "../api/tradeTransactionApi";
import AssetSearchInput from "../components/asset/AssetSearchInput";
import {usePortfolioContext} from "../context/portfolioContext";
import type {Market, TradeType} from "../types/tradeTransaction";

function createDefaultTradeTime() {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    return now.toISOString().slice(0, 16);
}

export default function TradeTransactionEntryPage() {
    const navigate = useNavigate();
    const {memberId, selectedPortfolioId} = usePortfolioContext();
    const [market, setMarket] = useState<Market>("US");
    const [ticker, setTicker] = useState("");
    const [tradeType, setTradeType] = useState<TradeType>("BUY");
    const [quantity, setQuantity] = useState("");
    const [executedPrice, setExecutedPrice] = useState("");
    const [fee, setFee] = useState("0");
    const [tradedAt, setTradedAt] = useState(createDefaultTradeTime);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (selectedPortfolioId === null) return;

        const normalizedTicker = ticker.trim().toUpperCase();
        const numericQuantity = Number(quantity);
        const numericPrice = Number(executedPrice);
        const numericFee = Number(fee);

        if (!normalizedTicker || ![numericQuantity, numericPrice, numericFee].every(Number.isFinite)
            || numericQuantity <= 0 || numericPrice <= 0 || numericFee < 0 || !tradedAt) {
            setErrorMessage("종목, 수량, 단가, 수수료, 체결 시각을 올바르게 입력해 주세요.");
            return;
        }

        setErrorMessage(null);
        setIsSubmitting(true);

        try {
            await createTradeTransaction(memberId, selectedPortfolioId, {
                market,
                ticker: normalizedTicker,
                tradeType,
                quantity: numericQuantity,
                executedPrice: numericPrice,
                fee: numericFee,
                tradedAt: new Date(tradedAt).toISOString(),
            });
            navigate("/holdings");
        } catch (reason) {
            setErrorMessage(reason instanceof Error ? reason.message : "매매 기록을 등록하지 못했습니다.");
            setIsSubmitting(false);
        }
    }

    return (
        <>
            <header className="page-header transaction-header">
                <div><p className="eyebrow">TRANSACTION</p><h1>매매 기록 등록</h1><p>실제 주문을 전송하지 않습니다. 체결된 매수·매도 내역을 기록해 보유 현황과 평가를 계산합니다.</p></div>
            </header>
            <form className="transaction-form" onSubmit={handleSubmit}>
                <section>
                    <h2>거래 정보</h2>
                    <div className="form-grid">
                        <label>시장<select value={market} onChange={(event) => setMarket(event.target.value as Market)}><option value="US">미국 (US)</option><option value="KR">한국 (KR)</option></select></label>
                        <label>거래 유형<select value={tradeType} onChange={(event) => setTradeType(event.target.value as TradeType)}><option value="BUY">매수</option><option value="SELL">매도</option></select></label>
                        <AssetSearchInput market={market} ticker={ticker} onTickerChange={setTicker}/>
                        <label>체결 시각<input type="datetime-local" value={tradedAt} onChange={(event) => setTradedAt(event.target.value)}/></label>
                    </div>
                </section>
                <section>
                    <h2>체결 수치</h2>
                    <div className="form-grid">
                        <label>수량<input type="number" value={quantity} onChange={(event) => setQuantity(event.target.value)} min="0" step="any" inputMode="decimal" placeholder="0"/></label>
                        <label>체결 단가<input type="number" value={executedPrice} onChange={(event) => setExecutedPrice(event.target.value)} min="0" step="any" inputMode="decimal" placeholder="0.00"/></label>
                        <label>수수료<input type="number" value={fee} onChange={(event) => setFee(event.target.value)} min="0" step="any" inputMode="decimal"/></label>
                    </div>
                </section>
                {errorMessage ? <p className="form-error-message" role="alert">{errorMessage}</p> : null}
                <div className="form-actions"><button type="button" className="secondary-button" onClick={() => navigate("/holdings")}>취소</button><button type="submit" disabled={isSubmitting}>{isSubmitting ? "등록 중..." : "매매 기록 등록"}</button></div>
            </form>
        </>
    );
}
