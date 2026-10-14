import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ananya, mockApi, renderApp } from './utils';

const impact = {
  periodDays: 90,
  completedExchanges: 12,
  activeMembers: 9,
  rupeesSavedEstimate: 12499,
  co2KgAvoidedEstimate: 34.5,
  hoursShared: 6,
  byCategory: [{ category: 'TOOLS', count: 5, rupeesSaved: 3000 }],
  methodology: [
    {
      label: 'Rupees saved',
      formula: '20% of item value for loans',
      assumption: 'An item is shared about 5 times',
    },
    { label: 'CO2e avoided', formula: 'category factor x share', assumption: 'Rough factors' },
  ],
  disclaimer: 'Estimates only. Not measurements.',
};

describe('impact', () => {
  it('labels derived numbers as estimates and renders methodology from the API', async () => {
    mockApi({ 'GET /impact/me': { body: impact } });
    renderApp('/impact');
    expect(await screen.findByText('₹12,499')).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('These are estimates.');
    expect(screen.getByRole('note')).toHaveTextContent('Estimates only. Not measurements.');
    expect(screen.getAllByText('Estimate')).toHaveLength(3);
    const meth = screen.getByRole('heading', { name: 'How we estimate' }).closest('section')!;
    expect(within(meth).getAllByRole('listitem')).toHaveLength(2);
    expect(within(meth).getByText('20% of item value for loans')).toBeInTheDocument();
  });

  it('uses the circle endpoint when a circle is chosen', async () => {
    const api = mockApi({ 'GET /circles/10/impact': { body: impact } });
    renderApp('/impact?circleId=10&days=30');
    await screen.findByText('₹12,499');
    expect(api.called('GET', '/circles/10/impact')[0].query.get('days')).toBe('30');
  });
});

const circle = (myRole: 'MEMBER' | 'MODERATOR') => ({
  id: 11,
  slug: 'bandra',
  name: 'Bandra West Commons',
  city: 'Mumbai',
  area: 'Bandra West',
  description: 'Lane-by-lane sharing',
  memberCount: 10,
  activeListingCount: 4,
  myRole,
  guidelines: 'Be kind.',
  moderators: [{ userId: 2, displayName: 'Rohan Mehta' }],
  myStatus: 'ACTIVE',
});

describe('moderation', () => {
  it('hides moderation from regular members and does not fetch reports', async () => {
    const api = mockApi({ 'GET /circles/11': { body: circle('MEMBER') } });
    renderApp('/circles/11/moderate');
    expect(await screen.findByText('Moderators only')).toBeInTheDocument();
    expect(api.called('GET', '/circles/11/reports')).toHaveLength(0);
  });

  it('circle detail only links to moderation for moderators', async () => {
    mockApi({
      'GET /circles/11': { body: circle('MEMBER') },
      'GET /circles/11/members': { body: [] },
      'GET /circles/11/activity': { body: [] },
    });
    renderApp('/circles/11');
    await screen.findByRole('heading', { name: 'Bandra West Commons' });
    expect(screen.queryByRole('link', { name: 'Moderate' })).not.toBeInTheDocument();
  });

  it('lets a moderator resolve a report and remove the listing', async () => {
    const api = mockApi({
      'GET /circles/11': { body: circle('MODERATOR') },
      'GET /circles/11/reports': {
        body: [
          {
            id: 3,
            targetType: 'LISTING',
            targetId: 7,
            targetLabel: 'Suspicious drill',
            reason: 'Looks like spam',
            reporter: { id: 5, displayName: 'Kabir' },
            status: 'OPEN',
            createdAt: '2026-10-13T10:00:00+05:30',
          },
        ],
      },
      'GET /circles/11/members': {
        body: [
          {
            userId: 1,
            displayName: 'Ananya Rao',
            role: 'MEMBER',
            status: 'ACTIVE',
            joinedAt: '2026-09-01T10:00:00+05:30',
            completedExchanges: 2,
          },
        ],
      },
      'GET /circles/11/moderation-log': { body: [] },
      'POST /circles/11/reports/3/resolve': { status: 204 },
      'PATCH /circles/11/members/1': { body: {} },
    });
    const user = userEvent.setup();
    renderApp('/circles/11/moderate');
    expect(await screen.findByText('Suspicious drill')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Review/ }));
    await user.click(screen.getByLabelText('Also remove the listing from the circle'));
    await user.type(screen.getByLabelText('Note for the log'), 'Spam');
    await user.click(screen.getByRole('button', { name: 'Resolve report' }));
    await screen.findByText('Report resolved.');
    expect(api.called('POST', '/circles/11/reports/3/resolve')[0].body).toEqual({
      resolution: 'RESOLVED',
      note: 'Spam',
      removeListing: true,
    });
    await user.selectOptions(screen.getByLabelText('Status for Ananya Rao'), 'SUSPENDED');
    await screen.findByText('Updated Ananya Rao.');
    expect(api.called('PATCH', '/circles/11/members/1')[0].body).toEqual({ status: 'SUSPENDED' });
    expect(ananya.circles[1].role).toBe('MODERATOR');
  });
});
