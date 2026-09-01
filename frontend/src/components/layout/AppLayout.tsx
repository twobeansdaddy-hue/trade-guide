import {NavLink, Outlet} from "react-router-dom";
import {usePortfolioContext} from "../../context/portfolioContext";
import SignInPage from "../../pages/SignInPage";

const navigation = [
    {to: "/", label: "대시보드", end: true},
    {to: "/holdings", label: "보유 종목"},
    {to: "/strategy-guides", label: "전략 가이드"},
    {to: "/settings", label: "설정"},
];

export default function AppLayout() {
    const {portfolios, selectedPortfolioId, viewer, isLoading, isAuthenticationRequired, errorMessage, selectPortfolio} = usePortfolioContext();

    if (isAuthenticationRequired) {
        return <SignInPage/>;
    }

    return (
        <div className="application-frame">
            <header className="topbar">
                <NavLink className="brand" to="/">
                    <span className="brand-mark">TG</span>
                    <span>Trade Guide</span>
                </NavLink>
                <div className="topbar-actions">
                    <p>{viewer?.nickname ?? "의사결정 지원"}</p>
                    <label className="portfolio-selector">
                        <span>포트폴리오</span>
                        <select value={selectedPortfolioId ?? ""} disabled={isLoading || portfolios.length === 0} onChange={(event) => selectPortfolio(Number(event.target.value))}>
                            {portfolios.map((portfolio) => <option key={portfolio.id} value={portfolio.id}>{portfolio.name}</option>)}
                        </select>
                    </label>
                </div>
            </header>
            <div className="workspace">
                <nav className="sidebar" aria-label="주요 메뉴">
                    {navigation.map(({to, label, end}) => (
                        <NavLink key={to} to={to} end={end} className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
                            {label}
                        </NavLink>
                    ))}
                </nav>
                <main className="page-content">
                    {isLoading ? <p className="status-message" aria-live="polite">작업공간을 준비하고 있습니다.</p> : errorMessage ? <p className="status-message error" role="alert">{errorMessage}</p> : selectedPortfolioId === null ? <p className="status-message">등록된 포트폴리오가 없습니다.</p> : <Outlet/>}
                </main>
            </div>
        </div>
    );
}
