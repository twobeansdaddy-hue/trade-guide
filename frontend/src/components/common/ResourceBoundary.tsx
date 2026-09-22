import type { ReactNode } from "react";
import { getApiRetryAfterSeconds, hasApiStatus, isMarketDataProviderNotConfigured, isMarketDataRateLimitExceeded } from "../../api/apiError";
import { StatusMessage } from "./StatusMessage";
import { Link } from "react-router-dom";

interface ResourceState<T> {
  data: T | null;
  error: Error | null;
  isLoading: boolean;
  refresh: () => void;
}

interface ResourceBoundaryProps<T> {
  resource: ResourceState<T>;
  loadingMessage?: string;
  renderData: (data: T, isRateLimited: boolean) => ReactNode;
}

export function ResourceBoundary<T>({ resource, loadingMessage = "불러오는 중입니다.", renderData }: ResourceBoundaryProps<T>) {
  const isRiskPolicyMissing = resource.error ? hasApiStatus(resource.error, 404) : false;
  const isProviderMissing = isMarketDataProviderNotConfigured(resource.error);
  const isRateLimit = isMarketDataRateLimitExceeded(resource.error);
  const rateLimitCooldown = getApiRetryAfterSeconds(resource.error);
  const hasData = resource.data !== null;

  if (resource.isLoading && !hasData) {
    return <StatusMessage kind="loading" message={loadingMessage} />;
  }

  if (isProviderMissing) {
    return (
      <StatusMessage 
        kind="action-required" 
        message="시장 데이터 제공자가 설정되지 않았습니다." 
        action={<Link to="/settings">설정으로 이동</Link>} 
      />
    );
  }

  if (isRiskPolicyMissing && !hasData) {
    return (
      <StatusMessage 
        kind="action-required" 
        message="위험 한도를 설정하면 종목별 비중 초과를 확인할 수 있습니다." 
        action={<Link to="/settings">위험 한도 설정</Link>} 
      />
    );
  }

  if (resource.error && !hasData) {
    if (isRateLimit) {
      return (
        <StatusMessage 
          kind="error" 
          message={resource.error.message || "요청 제한을 초과했습니다."} 
          action={<button onClick={resource.refresh}>다시 시도{rateLimitCooldown ? ` (${rateLimitCooldown}초)` : ""}</button>} 
        />
      );
    }
    return (
      <StatusMessage 
        kind="error" 
        message={resource.error.message} 
        action={<button onClick={resource.refresh}>다시 시도</button>} 
      />
    );
  }

  if (hasData) {
    return <>{renderData(resource.data as T, isRateLimit)}</>;
  }

  return null;
}
