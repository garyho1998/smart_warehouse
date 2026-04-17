const SEVERITY_STYLES = {
  HIGH: 'bg-red-50 border-red-300 text-red-800',
  MEDIUM: 'bg-amber-50 border-amber-300 text-amber-800',
  LOW: 'bg-blue-50 border-blue-300 text-blue-800',
};

const SEVERITY_LABELS = {
  HIGH: '高風險',
  MEDIUM: '中風險',
  LOW: '低風險',
};

export default function InsightBanner({ insights }) {
  if (!insights || insights.length === 0) return null;

  return (
    <div className="flex flex-col gap-2 mb-4">
      {insights.map((insight, i) => (
        <div
          key={i}
          className={`border rounded px-4 py-2 text-sm flex items-center gap-3 ${
            SEVERITY_STYLES[insight.severity] || SEVERITY_STYLES.LOW
          }`}
        >
          <span className="font-semibold shrink-0">
            {SEVERITY_LABELS[insight.severity] || insight.severity}
          </span>
          <span>{insight.message}</span>
        </div>
      ))}
    </div>
  );
}
