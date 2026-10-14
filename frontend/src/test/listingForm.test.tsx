import { screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, mockApi, renderApp } from './utils';
import { parseTags, validateListing } from '../pages/ListingFormPage';

describe('listing form', () => {
  const detail = { id: 99, title: 'x' };

  it('validates before submitting', async () => {
    const api = mockApi({});
    const user = userEvent.setup();
    renderApp('/listings/new');
    await screen.findByRole('heading', { name: 'Share something' });
    await user.type(screen.getByLabelText('Title'), 'ab');
    fireEvent.change(screen.getByLabelText('Available until'), { target: { value: '2020-01-01' } });
    await user.click(screen.getByRole('button', { name: 'Post listing' }));
    expect(await screen.findByText('Title must be 3 to 120 characters.')).toBeInTheDocument();
    expect(screen.getByText('The end date cannot be before the start date.')).toBeInTheDocument();
    expect(api.called('POST', '/listings')).toHaveLength(0);
  });

  it('posts the contract body and goes to the new listing', async () => {
    const api = mockApi({
      'POST /listings': { status: 201, body: detail },
      'GET /listings/99': {
        body: {
          ...detail,
          title: 'Pressure cooker',
          owner: { id: 1, displayName: 'Ananya Rao' },
          circleName: 'K',
          type: 'OFFER',
          kind: 'ITEM',
          category: 'KITCHEN',
          tags: [],
          status: 'ACTIVE',
          canEdit: true,
          returnExpected: true,
          completedExchangesOfOwner: 0,
          availableFrom: null,
          availableTo: null,
        },
      },
      'GET /listings/99/matches': { body: [] },
    });
    const user = userEvent.setup();
    renderApp('/listings/new');
    await screen.findByRole('heading', { name: 'Share something' });
    await user.type(screen.getByLabelText('Title'), 'Pressure cooker 5L');
    await user.selectOptions(screen.getByLabelText('Category'), 'KITCHEN');
    await user.type(screen.getByLabelText('Tags'), 'cooker, kitchen , diwali');
    await user.type(screen.getByLabelText('Estimated value (₹)'), '1499');
    await user.click(screen.getByRole('button', { name: 'Post listing' }));
    await screen.findByRole('heading', { name: 'Pressure cooker' });
    const body = api.called('POST', '/listings')[0].body as Record<string, unknown>;
    expect(body).toMatchObject({
      circleId: 10,
      type: 'OFFER',
      kind: 'ITEM',
      title: 'Pressure cooker 5L',
      category: 'KITCHEN',
      tags: ['cooker', 'kitchen', 'diwali'],
      estimatedValueInr: 1499,
      returnExpected: true,
    });
  });

  it('maps server field errors', async () => {
    mockApi({
      'POST /listings': apiError(400, 'VALIDATION_FAILED', 'Validation failed', [
        { field: 'description', message: 'size must be between 0 and 2000' },
      ]),
    });
    const user = userEvent.setup();
    renderApp('/listings/new');
    await user.type(await screen.findByLabelText('Title'), 'Folding table');
    await user.click(screen.getByRole('button', { name: 'Post listing' }));
    expect(await screen.findByText('size must be between 0 and 2000')).toBeInTheDocument();
    expect(screen.getByLabelText('Description')).toHaveAttribute('aria-invalid', 'true');
  });

  it('helpers', () => {
    expect(parseTags(' a, ,b ,')).toEqual(['a', 'b']);
    expect(
      validateListing({
        circleId: '1',
        type: 'OFFER',
        kind: 'ITEM',
        title: 'Drill',
        description: '',
        category: 'TOOLS',
        tags: '1,2,3,4,5,6,7,8,9',
        availableFrom: '2026-01-01',
        availableTo: '2026-01-02',
        pickupPreference: '',
        estimatedValueInr: '900000',
        depositInr: '',
        hoursEstimate: '',
        returnExpected: true,
      }),
    ).toMatchObject({ tags: 'Use at most 8 tags.', estimatedValueInr: expect.any(String) });
  });
});
