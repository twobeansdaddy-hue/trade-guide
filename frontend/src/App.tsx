import { useCallback, useEffect, useState } from "react";
import { getPortfolioRiskAlerts } from "./api/portfolioRiskApi";
import type { PortfolioRiskAlert } from "./types/portfolioRisk";
import "./App.css";

const MEMBER_ID = 1;
const PORTFOLIO_ID = 1;

function formatRate(rate: number) {
  return `${rate.toFixed(2)}%`;
}

function App() {
  const [riskAlerts, setRiskAlerts] = useState<PortfolioRiskAlert[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadRiskAlerts = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const alerts = await getPortfolioRiskAlerts(MEMBER_ID, PORTFOLIO_ID);
      setRiskAlerts(alerts);
    } catch (error) {
      const message =
          error instanceof Error
              ? error.message
              : "위험 경고를 불러오는 중 오류가 발생했습니다.";

      setErrorMessage(message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadRiskAlerts();
  }, [loadRiskAlerts]);

  return (
      <main className="app-shell">
        <header className="app-header">
          <p className="eyebrow">TRADE GUIDE</p>
          <h1>포트폴리오 위험 경고</h1>
          <p className="page-description">
            설정한 최대 단일 종목 비중을 초과한 보유 종목입니다.
          </p>
        </header>

        <section className="risk-section" aria-labelledby="risk-alert-title">
          <div className="section-heading">
            <div>
              <p className="section-label">Portfolio {PORTFOLIO_ID}</p>
              <h2 id="risk-alert-title">확인 필요</h2>
            </div>

            <div className="section-actions">
              <span className="alert-count">{riskAlerts.length}건</span>
              <button
                  type="button"
                  className="refresh-button"
                  onClick={loadRiskAlerts}
                  disabled={isLoading}
                  aria-label="위험 경고 새로고침"
                  title="위험 경고 새로고침"
              >
                ↻
              </button>
            </div>
          </div>

          {isLoading ? (
              <p className="status-message" aria-live="polite">
                위험 경고를 불러오는 중입니다.
              </p>
          ) : errorMessage ? (
              <p className="status-message error" role="alert">
                {errorMessage}
              </p>
          ) : riskAlerts.length > 0 ? (
              <ul className="risk-alert-list">
                {riskAlerts.map((alert) => (
                    <li
                        key={`${alert.market}-${alert.ticker}`}
                        className="risk-alert-item"
                    >
                      <div className="ticker-group">
                        <span className="market-badge">{alert.market}</span>
                        <strong>{alert.ticker}</strong>
                      </div>

                      <dl className="risk-details">
                        <div>
                          <dt>현재 비중</dt>
                          <dd>{formatRate(alert.exposureRate)}</dd>
                        </div>
                        <div>
                          <dt>최대 비중</dt>
                          <dd>{formatRate(alert.maxExposureRate)}</dd>
                        </div>
                      </dl>

                      <p className="risk-message">{alert.message}</p>
                    </li>
                ))}
              </ul>
          ) : (
              <p className="empty-state">현재 위험 경고가 없습니다.</p>
          )}
        </section>
      </main>
  );
}

export default App;