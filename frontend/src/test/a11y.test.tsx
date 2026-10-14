import { screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { mockApi, page, renderApp } from './utils';

async function check(container: HTMLElement) {
  const results = await axe(container, { rules: { region: { enabled: false } } });
  expect(results).toHaveNoViolations();
}

describe('accessibility (axe)', () => {
  it('login page', async () => {
    mockApi({}, { me: null });
    const { container } = renderApp('/login');
    await screen.findByRole('heading', { name: 'Sign in' });
    await check(container);
  });

  it('explore page', async () => {
    mockApi({
      'GET /listings': {
        body: page([
          {
            id: 1,
            circleId: 10,
            circleName: 'Koramangala',
            type: 'OFFER',
            kind: 'ITEM',
            title: 'Folding table',
            category: 'FURNITURE',
            tags: [],
            availableFrom: '2026-10-14',
            availableTo: '2026-11-01',
            pickupPreference: 'Lift lobby',
            estimatedValueInr: 1200,
            depositInr: 0,
            status: 'ACTIVE',
            owner: { id: 2, displayName: 'Rohan' },
            createdAt: '2026-10-10T10:00:00+05:30',
          },
        ]),
      },
    });
    const { container } = renderApp('/explore');
    await screen.findByRole('link', { name: 'Folding table' });
    await check(container);
  });

  it('impact page', async () => {
    mockApi({
      'GET /impact/me': {
        body: {
          periodDays: 90,
          completedExchanges: 1,
          activeMembers: 2,
          rupeesSavedEstimate: 300,
          co2KgAvoidedEstimate: 1.2,
          hoursShared: 0,
          byCategory: [{ category: 'TOOLS', count: 1, rupeesSaved: 300 }],
          methodology: [{ label: 'Rupees saved', formula: '20%', assumption: 'Shared 5 times' }],
          disclaimer: 'Estimates only.',
        },
      },
    });
    const { container } = renderApp('/impact');
    await screen.findByRole('heading', { name: 'How we estimate' });
    await check(container);
  });
});
