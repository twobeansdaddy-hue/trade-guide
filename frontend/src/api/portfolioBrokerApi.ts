import {getJsonResponse} from "./apiError";
import type {BrokerHoldingPreview, BrokerLinkCandidate, PortfolioBrokerLink} from "../types/portfolioBroker";
const base = (memberId: number, portfolioId: number) => `/api/members/${memberId}/portfolios/${portfolioId}`;
export const getBrokerLinkCandidates = async (m: number, p: number) => getJsonResponse<BrokerLinkCandidate[]>(await fetch(`${base(m, p)}/broker-link-candidates`), "연결 가능한 계좌를 불러오지 못했습니다.");
export const getPortfolioBrokerLinks = async (m: number, p: number) => getJsonResponse<PortfolioBrokerLink[]>(await fetch(`${base(m, p)}/broker-links`), "연결된 계좌를 불러오지 못했습니다.");
export const linkBrokerAccount = async (m: number, p: number, c: BrokerLinkCandidate) => getJsonResponse<PortfolioBrokerLink>(await fetch(`${base(m, p)}/broker-links/${c.brokerConnectionId}`, {method: "PUT", headers: {"Content-Type": "application/json"}, body: JSON.stringify({brokerAccountId: c.brokerAccountId})}), "증권사 계좌를 연결하지 못했습니다.");
export const getBrokerHoldingPreview = async (m: number, p: number) => getJsonResponse<BrokerHoldingPreview>(await fetch(`${base(m, p)}/broker-sync-preview`, {method: "POST"}), "증권사 보유 종목을 불러오지 못했습니다.");
