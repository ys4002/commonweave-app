import { screen, fireEvent, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockApi, renderApp } from './utils';
import type { HandoffDetail, TransitionAction } from '../api/types';

const base: HandoffDetail = {
  id: 5,
  status: 'PROPOSED',
  listing: { id: 7, title: 'Bosch drill with bits', category: 'TOOLS', type: 'OFFER' },
  role: 'PROVIDER',
  counterpart: { id: 3, displayName: 'Meera Iyer' },
  scheduledAt: null,
  returnBy: null,
  returnExpected: true,
  updatedAt: '2026-10-14T09:00:00+05:30',
  message: 'Can I pick it up Saturday?',
  pickupNote: null,
  pickupPreference: 'Society gate',
  events: [
    {
      fromStatus: null,
      toStatus: 'PROPOSED',
      actor: { id: 3, displayName: 'Meera Iyer' },
      note: null,
      at: '2026-10-14T09:00:00+05:30',
    },
  ],
  allowedActions: ['CONFIRM', 'CANCEL'],
};

const buttons = () => screen.getAllByRole('button').map((b) => b.textContent);
const withActions = (allowedActions: TransitionAction[], extra: Partial<HandoffDetail> = {}) => ({
  ...base,
  ...extra,
  allowedActions,
});

describe('handoff detail', () => {
  it('renders only the buttons named in allowedActions', async () => {
    mockApi({ 'GET /handoffs/5': { body: base } });
    renderApp('/handoffs/5');
    await screen.findByRole('button', { name: 'Confirm handoff' });
    expect(screen.getByRole('button', { name: 'Cancel handoff' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mark as handed off' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mark as returned' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mark as completed' })).not.toBeInTheDocument();
  });

  it.each([
    [
      ['MARK_HANDED_OFF', 'CANCEL'],
      ['Mark as handed off', 'Cancel handoff'],
    ],
    [['MARK_RETURNED'], ['Mark as returned']],
    [['COMPLETE'], ['Mark as completed']],
  ] as [TransitionAction[], string[]][])('allowedActions %j', async (actions, labels) => {
    mockApi({ 'GET /handoffs/5': { body: withActions(actions, { status: 'HANDED_OFF' }) } });
    renderApp('/handoffs/5');
    await screen.findByRole('heading', { name: 'Next steps' });
    const names = buttons();
    for (const l of labels) expect(names).toContain(l);
    for (const l of [
      'Confirm handoff',
      'Mark as handed off',
      'Mark as returned',
      'Mark as completed',
      'Cancel handoff',
    ]) {
      if (!labels.includes(l)) expect(names).not.toContain(l);
    }
  });

  it('shows no action buttons when allowedActions is empty', async () => {
    mockApi({ 'GET /handoffs/5': { body: withActions([], { status: 'COMPLETED' }) } });
    renderApp('/handoffs/5');
    await screen.findByText('No actions');
    expect(screen.queryByRole('button', { name: /handoff|Mark as/ })).not.toBeInTheDocument();
  });

  it('confirm requires a future date, then posts an IST ISO timestamp', async () => {
    const api = mockApi({
      'GET /handoffs/5': { body: base },
      'POST /handoffs/5/transitions': { body: withActions([], { status: 'SCHEDULED' }) },
    });
    const user = userEvent.setup();
    renderApp('/handoffs/5');
    await user.click(await screen.findByRole('button', { name: 'Confirm handoff' }));
    const form = screen.getByRole('form', { name: 'Schedule handoff' });
    await user.click(within(form).getByRole('button', { name: 'Schedule it' }));
    expect(await screen.findByText('Pick a date and time for the handoff.')).toBeInTheDocument();
    fireEvent.change(within(form).getByLabelText('Handoff date and time (IST)'), {
      target: { value: '2020-01-01T10:00' },
    });
    await user.click(within(form).getByRole('button', { name: 'Schedule it' }));
    expect(await screen.findByText('Pick a time in the future.')).toBeInTheDocument();
    expect(api.called('POST', '/handoffs/5/transitions')).toHaveLength(0);

    fireEvent.change(within(form).getByLabelText('Handoff date and time (IST)'), {
      target: { value: '2099-10-18T18:30' },
    });
    await user.type(within(form).getByLabelText('Pickup note'), 'Lift lobby');
    await user.click(within(form).getByRole('button', { name: 'Schedule it' }));
    await screen.findByText('Handoff confirmed and scheduled.');
    expect(api.called('POST', '/handoffs/5/transitions')[0].body).toEqual({
      action: 'CONFIRM',
      scheduledAt: '2099-10-18T18:30:00+05:30',
      pickupNote: 'Lift lobby',
    });
  });

  it('shows the API conflict message when a transition fails', async () => {
    mockApi({
      'GET /handoffs/5': { body: withActions(['MARK_HANDED_OFF'], { status: 'SCHEDULED' }) },
      'POST /handoffs/5/transitions': {
        status: 409,
        body: {
          status: 409,
          code: 'INVALID_TRANSITION',
          message: 'Already handed off',
          fieldErrors: [],
        },
      },
    });
    const user = userEvent.setup();
    renderApp('/handoffs/5');
    await user.click(await screen.findByRole('button', { name: 'Mark as handed off' }));
    expect(await screen.findByText('Already handed off')).toBeInTheDocument();
  });

  it('formats times in IST and lists the timeline', async () => {
    mockApi({
      'GET /handoffs/5': {
        body: withActions([], { status: 'SCHEDULED', scheduledAt: '2026-10-17T18:30:00+05:30' }),
      },
    });
    renderApp('/handoffs/5');
    expect(await screen.findByText('Sat, 17 Oct, 6:30 pm')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Handoff progress' })).toBeInTheDocument();
  });
});
