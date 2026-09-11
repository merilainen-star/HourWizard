# Manual actual-workshift entry

Inspected on 2026-09-11 from the public Finago Mobiili frontend:
https://app.tuntivelho.com/mobiili/static/js/main.9577daff7ecc85670913.js

This is frontend-derived protocol evidence, **not an authenticated production test**.

- `tyovuoroAdd` takes `tyyppi: TyovuoroEnumType!`, `tyopisteid: Int!`,
  `talaatuid: Int!`, `alku: Int!`, `loppu: Int!` and optional `taukokesto: Int`
  and `tietoja: String`. The native frontend also supports optional overtime,
  pay types, work type, break start, employee and shift marker fields.
- The frontend routes actual shifts with `type=tot` and supplies that as `tyyppi`.
- The frontend checks `errors` and a nonempty `tyovuoro.id` before reporting success.
- The frontend constructs the initial date using `setUTCHours(0,0,0,0)` and stores
  the editable start/end as wall-clock seconds. Its work calculation subtracts
  `taukokesto` from `loppu - alku`, so the break duration is also seconds.
- The Android flow supplies the authenticated user's freshly fetched clock-card
  defaults for workplace and work quality. It fetches `userProfile.henkiloid` and
  the matching `talaadut.defaultType`, verifies that the marker is in `tvmerkinnat`,
  and explicitly sends both `henkiloid` and `tvmerkintaid`, as the native form does.
  It also sends the native form's initial `taukoalku` (day's wall-clock midnight)
  and `tyontekijalukumaara=1`. Missing identity or marker stops the write.
  Account-specific acceptance still needs production verification.

## Reported server error

The first production attempt returned `Call to undefined method
Sentry\\EventType::clientReport()`. This is a server-side error reporting failure;
it does not establish the original cause or whether a write committed. The first
Android request omitted the employee and shift marker supplied by the native form.
Correcting that discrepancy is not proof that the Sentry problem is resolved.
The app retains the technical error and asks the user to check existing workshifts
before trying again. No test writes are sent to production.

The mutation creates an actual workshift, unlike `leimaTallenna`, which changes
the live clock-card direction. The Android feature must never implement a historical
shift as two live punches. Demo mode remains local; blank credentials are an error.

The POST has connection retries and redirects disabled. A connection loss or an
unconfirmed response is shown as an uncertain outcome, with instructions to check
the service before retrying. An identical successful local entry is also rejected.
This is not server-side idempotency: another device, cleared local history, or an
uncertain earlier request may still require checking for existing records.

One `TYÖVUORO` history record stores the requested time interval in its message.
Its raw details start with `MANUAL_SHIFT_MINUTES=<net minutes>` before the server
confirmation, so the existing weekly summary includes it without a second lunch
deduction or pairing it with an active punch. Current balance is refreshed separately;
it is not attached to the historical date as though it were that day's balance.
