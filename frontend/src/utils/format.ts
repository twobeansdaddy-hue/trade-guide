export function formatUsd(amount: number) {
    return Intl.NumberFormat("en-US", {style: "currency", currency: "USD"}).format(amount);
}

export function formatPercent(percent: number) {
    return `${percent.toFixed(2)}%`;
}

export function formatRatio(ratio: number) {
    return formatPercent(ratio * 100);
}

export function getProfitLossClassName(value: number) {
    if (value > 0) return "positive";
    if (value < 0) return "negative";
    return "neutral";
}
