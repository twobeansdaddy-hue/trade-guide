export default function SignInPage() {
    return (
        <main className="sign-in-page">
            <section className="sign-in-panel" aria-labelledby="sign-in-title">
                <div className="sign-in-brand"><span className="brand-mark">TG</span><span>Trade Guide</span></div>
                <p className="eyebrow">PORTFOLIO DECISION SUPPORT</p>
                <h1 id="sign-in-title">내 포트폴리오의 판단 근거를 한곳에서 확인하세요.</h1>
                <p className="sign-in-description">Trade Guide는 자동 주문을 실행하지 않습니다. 평가, 위험 경고, 전략 가이드를 보고 최종 판단은 직접 내립니다.</p>
                <a className="sign-in-button" href="/oauth2/authorization/google">Google로 계속하기</a>
                <p className="sign-in-note">로그인 후 연결된 포트폴리오만 조회할 수 있습니다.</p>
            </section>
        </main>
    );
}
