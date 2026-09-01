import {Navigate, Route, Routes} from "react-router-dom";
import AppLayout from "./components/layout/AppLayout";
import DashboardPage from "./pages/DashboardPage";
import HoldingsPage from "./pages/HoldingsPage";
import SettingsPage from "./pages/SettingsPage";
import StrategyGuidesPage from "./pages/StrategyGuidesPage";
import TradeTransactionEntryPage from "./pages/TradeTransactionEntryPage";
import PortfolioOnboardingPage from "./pages/PortfolioOnboardingPage";
import {PortfolioProvider} from "./context/PortfolioProvider";
import "./App.css";

export default function App() {
    return <PortfolioProvider><Routes><Route element={<AppLayout/>}><Route index element={<DashboardPage/>}/><Route path="holdings" element={<HoldingsPage/>}/><Route path="portfolios/new" element={<PortfolioOnboardingPage/>}/><Route path="transactions/new" element={<TradeTransactionEntryPage/>}/><Route path="strategy-guides" element={<StrategyGuidesPage/>}/><Route path="settings" element={<SettingsPage/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Route></Routes></PortfolioProvider>;
}
