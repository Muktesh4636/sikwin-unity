/** Indian grouping for money: 1,00,000.00 (not 100,000.00). */
export function formatIndian(value: string | number | null | undefined): string {
  const raw = typeof value === 'number' ? value : parseFloat(String(value ?? '0').replace(/,/g, '').replace(/₹/g, '').trim());
  if (Number.isNaN(raw)) return '0.00';
  return new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(raw);
}

export function formatRupee(value: string | number | null | undefined): string {
  return `₹${formatIndian(value)}`;
}
