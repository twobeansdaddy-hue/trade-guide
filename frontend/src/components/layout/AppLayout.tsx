import {NavLink, Outlet} from "react-router-dom";

const navigation = [
    {to: "/", label: "대시보드", end: true},
    {to: "/holdings", label: "보유 종목"},
    {to: "/strategy-guides", label: "전략 가이드"},
    {to: "/settings", label: "설정"},
];

export default function AppLayout() {
    return (
        <div className="application-frame">
            <header className="topbar">
                <NavLink className="brand" to="/">TRADE GUIDE</NavLink>
                <p>미국 주식 의사결정 지원</p>
            </header>
            <div className="workspace">
                <nav className="sidebar" aria-label="주요 메뉴">
                    {navigation.map(({to, label, end}) => (
                        <NavLink key={to} to={to} end={end} className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
                            {label}
                        </NavLink>
                    ))}
                </nav>
                <main className="page-content"><Outlet/></main>
            </div>
        </div>
    );
}
