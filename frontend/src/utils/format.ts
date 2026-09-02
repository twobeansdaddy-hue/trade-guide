export function formatUsd(amount: number) {
    return Intl.NumberFormat("en-US", {style: "currency", currency: "USD"}).format(amount);
}

export function formatPercent(percent: number) {
    return `${percent.toFixed(2)}%`;
}

export function formatRatio(ratio: number) {
    return formatPercent(ratio * 100);
}

export function formatDateTime(value: string) {
    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    }).format(new Date(value));
}

export function getProfitLossClassName(value: number) {
    if (value > 0) return "positive";
    if (value < 0) return "negative";
    return "neutral";
}
