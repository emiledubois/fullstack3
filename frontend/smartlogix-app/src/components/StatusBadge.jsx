const TONE_CLASSES = {
  success: "bg-success-bg text-success-text",
  warning: "bg-warning-bg text-warning-text",
  danger:  "bg-danger-bg text-danger-text",
  info:    "bg-info-bg text-info-text",
  accent:  "bg-accent-bg text-accent-text",
  chip:    "bg-chip-bg text-chip-text",
};

export default function StatusBadge({ tone = "chip", children, className = "" }) {
  return (
    <span
      className={`inline-flex items-center text-xs font-semibold px-2.5 py-1 rounded-full ${
        TONE_CLASSES[tone] || TONE_CLASSES.chip
      } ${className}`}
    >
      {children}
    </span>
  );
}
