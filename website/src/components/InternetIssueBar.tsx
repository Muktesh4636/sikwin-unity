type InternetIssueBarProps = {
  onRetry: () => void;
  message?: string;
};

/** Bottom banner for Roulette / Stock Market load failures. */
export function InternetIssueBar({
  onRetry,
  message = 'Internet issue. Please check your connection and try again.',
}: InternetIssueBarProps) {
  return (
    <div className="absolute inset-x-0 bottom-0 z-20 bg-black/95 px-5 pb-6 pt-4">
      <p className="text-center text-sm leading-5 text-textGrey">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-3 w-full rounded-xl bg-primaryYellow py-3 text-center text-base font-bold text-black"
      >
        Retry
      </button>
    </div>
  );
}
