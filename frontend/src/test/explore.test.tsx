import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockApi, page, renderApp } from './utils';
import type { ListingSummary } from '../api/types';

const drill: ListingSummary = {
  id: 7,
  circleId: 10,
  circleName: 'Koramangala 5th Block Neighbours',
  type: 'OFFER',
  kind: 'ITEM',
  title: 'Bosch drill with bits',
  category: 'TOOLS',
  tags: ['drill'],
  availableFrom: '2026-10-14',
  availableTo: '2026-11-14',
  pickupPreference: 'Society gate',
  estimatedValueInr: 4999,
  depositInr: 500,
  status: 'ACTIVE',
  owner: { id: 2, displayName: 'Rohan Mehta' },
  createdAt: '2026-10-10T09:00:00+05:30',
};

describe('explore', () => {
  it('renders listings with INR formatting', async () => {
    mockApi({ 'GET /listings': { body: page([drill]) } });
    renderApp('/explore');
    expect(await screen.findByRole('link', { name: 'Bosch drill with bits' })).toBeInTheDocument();
    expect(screen.getByText('₹4,999')).toBeInTheDocument();
    expect(screen.getByText(/₹500 deposit/)).toBeInTheDocument();
  });

  it('reads filters from the URL and sends them to the API', async () => {
    const api = mockApi({ 'GET /listings': { body: page([drill]) } });
    renderApp('/explore?type=REQUEST&category=TOOLS&q=drill&sort=soonest&page=1');
    await screen.findByRole('link', { name: 'Bosch drill with bits' });
    const q = api.called('GET', '/listings').at(-1)!.query;
    expect(q.get('type')).toBe('REQUEST');
    expect(q.get('category')).toBe('TOOLS');
    expect(q.get('q')).toBe('drill');
    expect(q.get('sort')).toBe('soonest');
    expect(q.get('page')).toBe('1');
    expect(screen.getByLabelText('Offer or request')).toHaveValue('REQUEST');
    expect(screen.getByLabelText('Search')).toHaveValue('drill');
  });

  it('changing filters re-queries the API, and clearing resets them', async () => {
    const api = mockApi({ 'GET /listings': { body: page([drill]) } });
    const user = userEvent.setup();
    renderApp('/explore');
    await screen.findByRole('link', { name: 'Bosch drill with bits' });
    await user.selectOptions(screen.getByLabelText('Circle'), '10');
    await user.selectOptions(screen.getByLabelText('Kind'), 'SKILL');
    fireEvent.change(screen.getByLabelText('Available on'), { target: { value: '2026-10-18' } });
    await user.type(screen.getByLabelText('Search'), 'cooker');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => {
      const q = api.called('GET', '/listings').at(-1)!.query;
      expect(q.get('circleId')).toBe('10');
      expect(q.get('kind')).toBe('SKILL');
      expect(q.get('availableOn')).toBe('2026-10-18');
      expect(q.get('q')).toBe('cooker');
    });
    await user.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(api.called('GET', '/listings').at(-1)!.query.get('q')).toBeNull());
    expect(screen.getByLabelText('Kind')).toHaveValue('');
  });

  it('paginates', async () => {
    const api = mockApi({
      'GET /listings': ({ url }) => ({
        body: page([drill], {
          page: Number(url.searchParams.get('page') ?? 0),
          totalPages: 3,
          totalElements: 30,
        }),
      }),
    });
    const user = userEvent.setup();
    renderApp('/explore');
    await screen.findByText('Page 1 of 3');
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await screen.findByText('Page 2 of 3');
    expect(api.called('GET', '/listings').at(-1)!.query.get('page')).toBe('1');
  });

  it('shows an empty state and an error state', async () => {
    mockApi({ 'GET /listings': { body: page([]) } });
    renderApp('/explore');
    expect(await screen.findByText('Nothing matches yet')).toBeInTheDocument();
  });

  it('shows a retryable error', async () => {
    mockApi({
      'GET /listings': {
        status: 500,
        body: { status: 500, code: 'INTERNAL', message: 'Server hiccup', fieldErrors: [] },
      },
    });
    renderApp('/explore');
    expect(await screen.findByRole('alert')).toHaveTextContent('Server hiccup');
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
