type RequestErrorProps = {
    message: string;
    onRetry: () => void;
    retryLabel: string;
};

export default function RequestError({message, onRetry, retryLabel}: RequestErrorProps) {
    return <div className="status-message error request-error" role="alert">
        <p>{message}</p>
        <button type="button" className="retry-button" onClick={onRetry}>
            {retryLabel}
        </button>
    </div>;
}
