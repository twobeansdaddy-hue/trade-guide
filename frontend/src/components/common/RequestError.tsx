type RequestErrorProps = {
    message: string;
    onRetry: () => void;
    retryLabel: string;
    className?: string;
};

export default function RequestError({message, onRetry, retryLabel, className}: RequestErrorProps) {
    return <div className={`status-message error request-error${className ? ` ${className}` : ""}`} role="alert">
        <p>{message}</p>
        <button type="button" className="retry-button" onClick={onRetry}>
            {retryLabel}
        </button>
    </div>;
}
