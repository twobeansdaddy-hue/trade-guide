import {useState, type FormEvent} from "react";
import {useNavigate} from "react-router-dom";
import {usePortfolioContext} from "../context/portfolioContext";

export default function PortfolioOnboardingPage() {
    const navigate = useNavigate();
    const {createPortfolio} = usePortfolioContext();
    const [name, setName] = useState("");
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        const normalizedName = name.trim();
        if (normalizedName.length === 0) {
            setErrorMessage("포트폴리오 이름을 입력해 주세요.");
            return;
        }

        setErrorMessage(null);
        setIsSubmitting(true);

        try {
            await createPortfolio(normalizedName);
            navigate("/");
        } catch (reason) {
            setErrorMessage(reason instanceof Error ? reason.message : "포트폴리오를 만들지 못했습니다.");
            setIsSubmitting(false);
        }
    }

    return (
        <main className="onboarding-page">
            <section className="onboarding-panel" aria-labelledby="onboarding-title">
                <p className="eyebrow">GET STARTED</p>
                <h1 id="onboarding-title">첫 포트폴리오를 만들어 시작하세요.</h1>
                <p>포트폴리오는 보유 종목, 평가, 위험 한도, 전략 가이드를 묶는 작업 단위입니다.</p>
                <form onSubmit={handleSubmit}>
                    <label>
                        포트폴리오 이름
                        <input value={name} onChange={(event) => setName(event.target.value)} placeholder="예: 미국 장기 투자" maxLength={255} autoFocus/>
                    </label>
                    {errorMessage ? <p className="form-error-message" role="alert">{errorMessage}</p> : null}
                    <button type="submit" disabled={isSubmitting}>{isSubmitting ? "만드는 중..." : "포트폴리오 만들기"}</button>
                </form>
                <p className="onboarding-note">생성 후 거래 기록을 등록하면 현재 평가와 위험 경고를 확인할 수 있습니다.</p>
            </section>
        </main>
    );
}
