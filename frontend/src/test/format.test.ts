import { formatDate, formatDateTime, formatInr, istLocalToIso } from '../lib/format';

describe('format', () => {
  it('formats rupees with Indian grouping', () => {
    expect(formatInr(1499)).toBe('₹1,499');
    expect(formatInr(1250000)).toBe('₹12,50,000');
    expect(formatInr(null)).toBe('₹0');
  });
  it('formats date-times in Asia/Kolkata', () => {
    expect(formatDateTime('2026-10-17T18:30:00+05:30')).toBe('Sat, 17 Oct, 6:30 pm');
    expect(formatDateTime('2026-10-17T13:00:00Z')).toBe('Sat, 17 Oct, 6:30 pm');
  });
  it('formats plain dates without shifting the day', () => {
    expect(formatDate('2026-10-18')).toBe('18 Oct 2026');
  });
  it('builds IST ISO strings from datetime-local values', () => {
    expect(istLocalToIso('2026-10-18T18:30')).toBe('2026-10-18T18:30:00+05:30');
  });
});
