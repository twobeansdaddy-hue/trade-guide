import {Navigate, Route, Routes} from "react-router-dom";
import AppLayout from "./components/layout/AppLayout";
import DashboardPage from "./pages/DashboardPage";
import HoldingsPage from "./pages/HoldingsPage";
import SettingsPage from "./pages/SettingsPage";
import StrategyGuidesPage from "./pages/StrategyGuidesPage";
import "./App.css";

export default function App() {
    return <Routes><Route element={<AppLayout/>}><Route index element={<DashboardPage/>}/><Route path="holdings" element={<HoldingsPage/>}/><Route path="strategy-guides" element={<StrategyGuidesPage/>}/><Route path="settings" element={<SettingsPage/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Route></Routes>;
}
