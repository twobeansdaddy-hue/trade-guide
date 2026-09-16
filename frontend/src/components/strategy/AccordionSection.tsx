import React, { useEffect, useId, useState } from "react";

export type AccordionSectionProps = {
    id?: string;
    anchorIds?: string[];
    defaultOpen?: boolean;
    sectionLabel?: string;
    title: React.ReactNode;
    summary?: React.ReactNode;
    headerExtra?: React.ReactNode;
    className?: string;
    headingLevel?: "h2" | "h3";
    children: React.ReactNode;
    onToggle?: (isOpen: boolean) => void;
};

export default function AccordionSection({
    id,
    anchorIds,
    defaultOpen = false,
    sectionLabel,
    title,
    summary,
    headerExtra,
    className = "",
    headingLevel = "h2",
    children,
    onToggle,
}: AccordionSectionProps) {
    const [isOpen, setIsOpen] = useState(() => {
        if (defaultOpen) return true;
        if (typeof window !== "undefined" && window.location.hash) {
            const currentHash = window.location.hash.replace(/^#/, "");
            const allAnchors = [
                ...(id ? [id] : []),
                ...(anchorIds ?? []),
            ];
            if (allAnchors.includes(currentHash)) {
                return true;
            }
        }
        return false;
    });

    const generatedId = useId();
    const contentId = `${id ?? generatedId}-content`;
    const headingId = `${id ?? generatedId}-heading`;
    const HeadingTag = headingLevel;

    // Listen for URL hash navigation to automatically expand and scroll
    useEffect(() => {
        const scrollToTarget = (targetHash: string) => {
            const allAnchors = [
                ...(id ? [id] : []),
                ...(anchorIds ?? []),
            ];

            if (allAnchors.includes(targetHash)) {
                setIsOpen(true);
                onToggle?.(true);

                // Delay allows React to render un-hidden content before scrolling
                setTimeout(() => {
                    const targetElement =
                        document.getElementById(targetHash) ??
                        (id ? document.getElementById(id) : null);
                    if (targetElement) {
                        targetElement.scrollIntoView({
                            behavior: "smooth",
                            block: "start",
                        });
                    }
                }, 100);
            }
        };

        const handleAnchorCheck = () => {
            const currentHash = window.location.hash.replace(/^#/, "");
            if (!currentHash) return;
            scrollToTarget(currentHash);
        };

        handleAnchorCheck();
        window.addEventListener("hashchange", handleAnchorCheck);

        const handleCustomExpand = (e: Event) => {
            const customEvent = e as CustomEvent<string>;
            scrollToTarget(customEvent.detail);
        };
        window.addEventListener("expand-accordion", handleCustomExpand as EventListener);

        const handleDocumentClick = (e: MouseEvent) => {
            const target = (e.target as HTMLElement).closest("a");
            if (!target) return;
            const href = target.getAttribute("href");
            if (href && href.startsWith("#")) {
                const anchor = href.slice(1);
                scrollToTarget(anchor);
            }
        };
        document.addEventListener("click", handleDocumentClick);

        return () => {
            window.removeEventListener("hashchange", handleAnchorCheck);
            window.removeEventListener("expand-accordion", handleCustomExpand as EventListener);
            document.removeEventListener("click", handleDocumentClick);
        };
    }, [id, anchorIds, onToggle]);

    const handleToggle = () => {
        const nextState = !isOpen;
        setIsOpen(nextState);
        onToggle?.(nextState);
    };

    const sectionClasses = [
        className.includes("content-section") || className.includes("premarket-guide-panel") ? "" : "content-section",
        "accordion-section",
        isOpen ? "is-open" : "is-collapsed",
        className,
    ].filter(Boolean).join(" ");

    return (
        <section
            id={id}
            className={sectionClasses}
            aria-labelledby={headingId}
        >
            <div className="accordion-header-row">
                <div className="accordion-heading-group">
                    {sectionLabel ? (
                        <p className="section-label">{sectionLabel}</p>
                    ) : null}
                    <HeadingTag id={headingId} className="accordion-heading">
                        <button
                            type="button"
                            className="accordion-toggle-btn"
                            aria-expanded={isOpen}
                            aria-controls={contentId}
                            onClick={handleToggle}
                        >
                            <span className="accordion-title-container">
                                <span className="accordion-title-text">{title}</span>
                                {summary ? (
                                    <span className="accordion-summary-chip">{summary}</span>
                                ) : null}
                            </span>
                            <span className="accordion-toggle-indicator">
                                <span className="accordion-toggle-text">
                                    {isOpen ? "접기" : "펼치기"}
                                </span>
                                <span
                                    className={`accordion-chevron-icon ${isOpen ? "is-expanded" : ""}`}
                                    aria-hidden="true"
                                >
                                    ▾
                                </span>
                            </span>
                        </button>
                    </HeadingTag>
                </div>
                {headerExtra ? (
                    <div className="accordion-header-extra">{headerExtra}</div>
                ) : null}
            </div>
            <div
                id={contentId}
                className="accordion-content"
                hidden={!isOpen}
                role="region"
                aria-labelledby={headingId}
            >
                {children}
            </div>
        </section>
    );
}
