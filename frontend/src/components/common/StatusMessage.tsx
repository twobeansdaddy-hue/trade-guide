import type { ReactNode } from "react";
import "../../styles/status.css";

export type StatusKind = "loading" | "empty" | "action-required" | "error";

interface StatusMessageProps {
  kind: StatusKind;
  message: string;
  action?: ReactNode;
}

export function StatusMessage({ kind, message, action }: StatusMessageProps) {
  const isAlert = kind === "error";
  const isLive = kind === "loading";

  return (
    <div className={`status-box status-${kind}`} role={isAlert ? "alert" : undefined} aria-live={isLive ? "polite" : undefined}>
      <p className="status-text">{message}</p>
      {action && <div className="status-action">{action}</div>}
    </div>
  );
}
