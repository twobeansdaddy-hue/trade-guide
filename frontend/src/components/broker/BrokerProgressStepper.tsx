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
    onSelectStep: (step: number) => void;
};

export default function BrokerProgressStepper({
    steps,
        onSelectStep,
}: BrokerProgressStepperProps) {
    return (
        <nav className="broker-stepper" aria-label="증권사 연동 절차 단계">
            <div className="broker-stepper-grid">
                {steps.map((item) => {
                    const isCompleted = item.isCompleted;
                    const isCurrent = item.isCurrent;
                    const isActionable = item.isActionable;
                    
                    let statusClass = "status-wait";
                    let accentClass = "";
                    if (isCompleted) statusClass = "status-done";
                    else if (isActionable) statusClass = "status-action";
                    
                    if (isCurrent) accentClass = "current-step";

                    return (
                        <button
                            key={item.step}
                            type="button"
                            className={`broker-step-card ${accentClass}`}
                            onClick={() => onSelectStep(item.step)}
                            aria-current={isCurrent ? "step" : undefined}
                            aria-label={`${item.step}단계: ${item.label} (${item.statusText})`}
                        >
                            <div className="step-card-number">{item.step}</div>
                            <div className="step-card-label">{item.label}</div>
                            <div className={`step-card-status ${statusClass}`}>{item.statusText}</div>
                        </button>
                    );
                })}
            </div>
        </nav>
    );
}
