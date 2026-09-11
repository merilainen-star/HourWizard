> **Unofficial project.** Numbawang is not affiliated with, endorsed by, or
> supported by Mepco Oy / Accountor Finago. "Tuntivelho" is their trademark
> and is used here only to identify the service this client interoperates
> with. Use with your own credentials and at your own risk.

# Numbawang
An unofficial Android/Termux client that punches in and out of the Tuntivelho timecard service.

### Forgotten workshifts

Open History and select **Lisää unohtunut työvuoro**. Enter the date, start and end
times in Finnish time, and the unpaid break in minutes. Select the next-day option
for an overnight shift. The app saves an actual workshift (`tyovuoroAdd`, `tot`)
using your default workplace and work type, leaving the current punch state intact.
The shift appears in local history and the weekly summary. Demo mode saves locally.

Saving requires permission to add actual workshifts in Tuntivelho. Server errors
remain visible in the form. If a connection drops after submission, check the
service before trying again: a write may have succeeded without a response.

The API request is derived from the public Finago Mobiili bundle
`main.9577daff7ecc85670913.js` inspected on 2026-09-11. Authenticated production
acceptance (including account-specific defaults and permissions) still needs a
real forgotten shift; development tests never submit fabricated work records.

## Installation
Clone the repository to `~/numbawang`.
Run `./setup.sh` inside the directory.

## Migration Note
**Warning:** The `applicationId` has changed to `com.numbawang.leimaus`. Android treats this as a NEW app. The old app is not upgraded; it must be uninstalled, and any stored credentials or history from the old app are lost.
