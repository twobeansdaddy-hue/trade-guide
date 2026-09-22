import {NavLink, Outlet} from "react-router-dom";
import {usePortfolioContext} from "../../context/portfolioContext";
import {useTheme} from "../../context/ThemeProvider";
import SignInPage from "../../pages/SignInPage";
import PortfolioOnboardingPage from "../../pages/PortfolioOnboardingPage";
import { StatusMessage } from "../common/StatusMessage";
import "../../styles/layout.css";

const navigation = [
    {to: "/", label: "대시보드", end: true},
    {to: "/holdings", label: "보유 종목"},
    {to: "/transactions", label: "매매 기록"},
    {to: "/strategy-guides", label: "전략 가이드"},
    {to: "/broker-accounts", label: "연동 계좌"},
    {to: "/settings", label: "설정"},
];

export default function AppLayout() {
    const {portfolios, selectedPortfolioId, viewer, isLoading, isAuthenticationRequired, errorMessage, selectPortfolio} = usePortfolioContext();
    const {theme, setTheme} = useTheme();

    if (isAuthenticationRequired) {
        return <SignInPage/>;
    }

    return (
        <div className="application-frame">
            <header className="topbar">
                <div className="topbar-content">
                    <NavLink className="brand" to="/">
                        <span className="brand-mark">TG</span>
                        <span>Trade Guide</span>
                    </NavLink>
                    
                    <div className="topbar-actions">
                        <div className="theme-toggle" role="group" aria-label="테마 전환">
                            <button 
                                aria-pressed={theme === 'light'} 
                                onClick={() => setTheme('light')}
                                className={theme === 'light' ? 'active' : ''}
                            >
                                Light
                            </button>
                            <button 
                                aria-pressed={theme === 'dark'} 
                                onClick={() => setTheme('dark')}
                                className={theme === 'dark' ? 'active' : ''}
                            >
                                Dark
                            </button>
                        </div>
                        
                        <label className="portfolio-selector">
                            <span className="sr-only">포트폴리오 선택</span>
                            <select value={selectedPortfolioId ?? ""} disabled={isLoading || portfolios.length === 0} onChange={(event) => selectPortfolio(Number(event.target.value))}>
                                {portfolios.map((portfolio) => <option key={portfolio.id} value={portfolio.id}>{portfolio.name}</option>)}
                            </select>
                        </label>

                        <button className="account-button">
                            <div className="account-initial">
                                {viewer?.nickname?.[0] ?? "U"}
                            </div>
                            <div className="account-info">
                                <div className="account-name">{viewer?.nickname ?? "사용자"}</div>
                                <div className="account-email">{viewer?.email ?? ""}</div>
                            </div>
                            <span className="account-chevron">▾</span>
                        </button>
                    </div>
                </div>

                <div className="app-tabs-container" style={{ position: "relative", maxWidth: "1440px", margin: "0 auto" }}>
                    <nav className="app-tabs" aria-label="주요 메뉴" onScroll={(e) => {
                        const target = e.currentTarget;
                        const hasScroll = target.scrollWidth > target.clientWidth;
                        const isAtEnd = Math.abs(target.scrollWidth - target.clientWidth - target.scrollLeft) < 2;
                        if (hasScroll && !isAtEnd) {
                            target.parentElement?.setAttribute("data-scrollable", "true");
                        } else {
                            target.parentElement?.removeAttribute("data-scrollable");
                        }
                    }}
                    ref={(el) => {
                        if (el) {
                            const hasScroll = el.scrollWidth > el.clientWidth;
                            if (hasScroll) el.parentElement?.setAttribute("data-scrollable", "true");
                        }
                    }}>
                        {navigation.map(({to, label, end}) => (
                            <NavLink key={to} to={to} end={end} className={({isActive}) => isActive ? "tab-link active" : "tab-link"}>
                                {label}
                            </NavLink>
                        ))}
                    </nav>
                </div>
            </header>
            
            <main className="page-content">
                {isLoading ? (
                    <StatusMessage kind="loading" message="작업공간을 준비하고 있습니다." />
                ) : errorMessage ? (
                    <StatusMessage kind="error" message={errorMessage} />
                ) : selectedPortfolioId === null ? (
                    <PortfolioOnboardingPage/>
                ) : (
                    <Outlet/>
                )}
            </main>
        </div>
    );
}
