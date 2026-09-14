export type BrokerStepItem = {
    step: number;
    id: string;
    label: string;
    shortLabel: string;
    statusText: string;
    isCompleted: boolean;
    isCurrent: boolean;
    isActionable: boolean;
};

type BrokerProgressStepperProps = {
    steps: BrokerStepItem[];
    currentStep: number;
    onSelectStep: (step: number) => void;
};

export default function BrokerProgressStepper({
    steps,
    currentStep,
    onSelectStep,
}: BrokerProgressStepperProps) {
    const currentStepItem = steps.find((s) => s.step === currentStep) ?? steps[0];
    const progressPercent = Math.round((currentStep / steps.length) * 100);

    return (
        <nav className="broker-stepper" aria-label="증권사 연동 절차 단계">
            {/* Mobile compact progress summary */}
            <div className="broker-stepper-mobile-header" aria-hidden="true">
                <div className="broker-stepper-mobile-info">
                    <span className="broker-stepper-mobile-step">
                        {currentStep} / {steps.length} 단계
                    </span>
                    <strong className="broker-stepper-mobile-title">
                        {currentStepItem.label}
                    </strong>
                    <span
                        className={`stepper-status-pill ${
                            currentStepItem.isCompleted
                                ? "pill-done"
                                : currentStepItem.isActionable
                                  ? "pill-action"
                                  : "pill-wait"
                        }`}
                    >
                        {currentStepItem.statusText}
                    </span>
                </div>
                <div className="broker-stepper-progress-bar">
                    <div
                        className="broker-stepper-progress-fill"
                        style={{width: `${progressPercent}%`}}
                    />
                </div>
            </div>

            {/* Step list for desktop and scrollable on small screens */}
            <ol className="broker-stepper-list">
                {steps.map((item, index) => {
                    const isPassed = item.isCompleted && !item.isCurrent;
                    const statusClass = item.isCompleted
                        ? "pill-done"
                        : item.isActionable
                          ? "pill-action"
                          : "pill-wait";

                    return (
                        <li
                            key={item.step}
                            className={`broker-stepper-item ${item.isCurrent ? "is-current" : ""} ${
                                item.isCompleted ? "is-completed" : ""
                            }`}
                            aria-current={item.isCurrent ? "step" : undefined}
                        >
                            <button
                                type="button"
                                className={`broker-stepper-btn ${item.isCurrent ? "active" : ""}`}
                                onClick={() => onSelectStep(item.step)}
                                aria-label={`${item.step}단계: ${item.label} (${item.statusText})`}
                            >
                                <span
                                    className={`stepper-circle ${
                                        item.isCurrent
                                            ? "circle-current"
                                            : isPassed
                                              ? "circle-completed"
                                              : "circle-pending"
                                    }`}
                                >
                                    {isPassed ? "✓" : item.step}
                                </span>
                                <div className="stepper-text">
                                    <span className="stepper-label">{item.label}</span>
                                    <span className={`stepper-status-pill ${statusClass}`}>
                                        {item.statusText}
                                    </span>
                                </div>
                            </button>
                            {index < steps.length - 1 ? (
                                <div
                                    className={`stepper-connector ${
                                        item.isCompleted ? "connector-completed" : ""
                                    }`}
                                    aria-hidden="true"
                                />
                            ) : null}
                        </li>
                    );
                })}
            </ol>
        </nav>
    );
}
