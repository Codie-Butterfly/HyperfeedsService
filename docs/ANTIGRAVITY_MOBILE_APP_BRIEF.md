# Antigravity build brief: Hyperfeeds mobile app

Build a production-quality Flutter application for Android and iOS named **Hyperfeeds**.
The app serves customers first, with role-aware employee tools exposed after employee
login. Use Dart null safety, Material 3, Riverpod for state management, GoRouter for
navigation, Dio for HTTP, and `flutter_secure_storage` for tokens. Keep API, domain,
and presentation layers separate. Do not hard-code demo data into production screens.

## Source of truth

The backend is the Spring Boot project in this repository. Its API is rooted at
`/api`. Read the controllers and DTO records before implementing each client model;
do not invent endpoints. Configure the origin using `--dart-define=API_BASE_URL=...`.
For Android emulator local development use `http://10.0.2.2:8080/api`; for iOS
Simulator use `http://localhost:8080/api`.

The app must handle loading, empty, offline, validation, unauthorized, forbidden,
conflict, and server-error states. Show useful user language, never raw stack traces.
JSON returned from typed Java records is camelCase. Some JDBC-backed list endpoints
return snake_case keys; map these explicitly in the relevant DTOs.

## Supplied visual assets

Copy these downloaded files into `assets/images/` and declare them in `pubspec.yaml`.
Rename files containing spaces during the copy and retain the mapping in a short
`assets/README.md`.

| Source file | Destination | Intended use |
|---|---|---|
| `/Users/sithokozilendlovu/Downloads/hyperfeeds1.png` | `hyperfeeds_logo.png` | App bar, sign-in, splash |
| `/Users/sithokozilendlovu/Downloads/hyperfeedschicken.png` | `animal_chicken.png` | Chick booking/category tile |
| `/Users/sithokozilendlovu/Downloads/hyperfeeds chickens.jpg` | `hero_chickens.jpg` | Chick availability hero |
| `/Users/sithokozilendlovu/Downloads/hyperfeedscow.jpeg` | `animal_cow_silhouette.jpg` | Cattle category |
| `/Users/sithokozilendlovu/Downloads/hyperfeeds cow.png` | `animal_cow_icon.png` | Compact cattle icon |
| `/Users/sithokozilendlovu/Downloads/hyperfeedsgoat.jpeg` | `animal_goat.jpg` | Goat category |
| `/Users/sithokozilendlovu/Downloads/hyperfeedsrabit.jpeg` | `animal_rabbit.jpg` | Rabbit category |
| `/Users/sithokozilendlovu/Downloads/hyperfeedscat.avif` | `animal_cat.avif` | Cat category; convert to PNG if Flutter target support is unreliable |
| `/Users/sithokozilendlovu/Downloads/hyperfeedsdog.png` | `animal_dog_icon.png` | Compact dog icon |
| `/Users/sithokozilendlovu/Downloads/hyperfeedsdogs.png` | `animal_dogs.png` | Dog category/hero |

Do not stretch or recolor the logo. Crop photographic assets with `BoxFit.cover` and
render silhouettes with `BoxFit.contain`. Generate Android/iOS launcher icons from
the logo only after placing it on a square navy background with safe padding.

## Visual system

Derive the theme from the supplied logo:

- Primary navy: approximately `#07043A`
- Brand orange: approximately `#FF7A00`
- Background: warm off-white `#FAFAF7`
- Surface: white
- Success: `#268A45`; warning: `#D97706`; error: `#B42318`
- Rounded cards: 16 px; controls: 12 px; minimum touch target: 48 px
- Use a clean sans-serif system font and support text scaling to 200%

The design should feel practical, trustworthy, agricultural, and modern. Avoid visual
clutter, excessive gradients, tiny text, and generic stock imagery. Support light and
dark themes, screen readers, high contrast, and reduced motion.

## App structure and navigation

Customer bottom navigation:

1. **Home** — greeting, selected branch, active announcement, specials, category
   shortcuts, chick availability teaser.
2. **Shop** — search, category filters, product cards, branch price and availability,
   cart.
3. **Chicks** — batch availability, breed/date/price/quantity, booking creation and
   booking history.
4. **Ask** — livestock question form and question/answer history. Clearly label expert
   answers; never label an AI draft as medical advice.
5. **Account** — orders, bookings, in-app messages, selected branch, sign out.

Show cart and unread-message badges. Persist the selected branch locally and require a
branch selection before catalogue, cart, or chick operations.

Employee access starts from a discreet **Employee sign in** action on the customer
sign-in screen. After login, build role-aware navigation:

- `ADMIN`: branches, categories/products, pricing/inventory, CSV import,
  announcements/specials, chick batches, livestock review.
- `BRANCH_MANAGER`: only assigned-branch editing, price/inventory, and chick batches.
- `ANIMAL_HEALTH_EXPERT`: pending livestock reviews and answer publishing.
- Other roles: show only explicitly authorized tools; do not infer access client-side.

The backend remains authoritative. A hidden button is not security: handle HTTP 403
cleanly and never attempt unauthorized mutations.

## Authentication

Customer onboarding:

1. `POST /auth/customers/signup` with `phoneNumber`, `firstName`, `lastName`.
2. Display the masked destination, expiry countdown, and resend cooldown returned by
   the backend.
3. `POST /auth/customers/verify-phone` with `challengeId` and six-digit `code`.
4. Securely store `accessToken` and `refreshToken` from the response.
5. Refresh through `POST /auth/customers/refresh`.

Employee authentication uses `POST /auth/employees/login` and
`POST /auth/employees/refresh`. Employee logout uses
`POST /auth/employees/logout`. Never log tokens, OTP values, credentials, Paynow
details, or personally identifiable information.

Create a single Dio authentication interceptor. Attach `Authorization: Bearer TOKEN`,
perform only one refresh when simultaneous requests receive 401, retry each request
at most once, and return to sign-in when refresh fails. Store tokens only in secure
storage, not SharedPreferences.

Important: the current backend uses a development OTP sender. Keep the OTP UI and API
flow, but do not claim an SMS was sent. Use neutral copy: **“Enter your verification
code.”** In-app messages are for authenticated notifications and are not a secure way
to deliver a pre-authentication OTP.

## Customer API flows

- Branches: `GET /branches`, `GET /branches/{id}`.
- Categories: `GET /catalogue/categories`.
- Products: `GET /catalogue/products?branchId=...&categoryId=...&q=...`.
- Cart: `GET /commerce/cart`, `PUT /commerce/cart/items/{productId}` with `branchId`
  and `quantity`, `DELETE /commerce/cart/items/{productId}`.
- Checkout: `POST /commerce/checkout`. Display returned Paynow instructions and
  reference. Paynow uses a handset mobile-money authorization prompt; do not embed a
  fake web checkout. Poll `GET /commerce/orders` until status changes and also surface
  the resulting in-app message.
- Orders: `GET /commerce/orders`.
- Chick availability: `GET /chicks/availability?branchId=...`.
- Chick bookings: `POST /chicks/bookings`, `GET /chicks/bookings`,
  `DELETE /chicks/bookings/{id}`. Show expiry and status (`HELD`, `CANCELLED`,
  `EXPIRED`) distinctly.
- Announcements and specials: `GET /content/announcements?branchId=...` and
  `GET /content/specials?branchId=...`.
- In-app messages: `GET /notifications`, optionally `?unreadOnly=true`, and
  `PUT /notifications/{id}/read`. Refresh on app resume and support pull-to-refresh;
  do not add SMS delivery.
- Livestock support: `POST /livestock/questions` and `GET /livestock/questions`.
  Show `AWAITING_EXPERT` while pending. Customers must never receive `ai_draft`.

## Employee API flows

Implement forms with client validation matching backend constraints:

- Branch creation/update: `POST /branches`, `PUT /branches/{id}`.
- Category/product management: `POST /catalogue/categories`,
  `POST /catalogue/products`, `PUT /catalogue/products/{id}`.
- Branch price/inventory: `PUT /catalogue/branches/{branchId}/products/{productId}/price`
  and `/inventory`.
- CSV import: multipart `POST /catalogue/import`, field name `file`, maximum 5 MB.
  Show created/updated/total results and backend row errors.
- Chick batches: `POST /chicks/batches`.
- Announcements/specials: `POST /content/announcements`, `POST /content/specials`.
- Expert queue: `GET /livestock/questions/review`; answer using
  `PUT /livestock/questions/{id}/answer`. Visually separate the private AI draft from
  the editable expert answer and require confirmation before publishing.

## State, caching, and resilience

- Use immutable Freezed models if code generation is available; otherwise immutable
  hand-written models with tested JSON factories.
- Cache branches, categories, and the last successful catalogue response in a local
  database such as Drift. Mark cached views as offline and disable mutations offline.
- Debounce product search by 300–400 ms.
- Use optimistic UI only for marking messages read and cart quantity changes; roll
  back visibly if the API rejects the request.
- Format money with `intl`, retaining the API currency. Never use floating-point math
  for totals; parse amounts into decimal-safe representations.
- Store timestamps as UTC and render in the device timezone.

## Testing and quality gates

Create:

- Unit tests for DTO parsing, token refresh coordination, money formatting, phone
  input, role routing, and cart calculations.
- Widget tests for sign-up/OTP, branch selection, empty/error catalogue, cart,
  checkout instructions, notification inbox, and expert answer review.
- Integration tests with a mocked HTTP server covering 401 refresh, 403, 409 stock
  conflict, 422 invalid OTP, and Paynow pending-to-paid order transitions.
- At least one Android and one iOS golden test for the home screen.

Run `dart format`, `flutter analyze`, and `flutter test` with zero failures. Do not
silence analyzer rules to make the build pass.

## Delivery requirements

- Commit a complete Flutter project with Android and iOS targets.
- Include `README.md` setup instructions and all required `--dart-define` values.
- Include an architecture note and a screen/API mapping table.
- Include the copied/renamed assets and their attribution/source mapping.
- Never commit production credentials, tokens, signing keys, or `.env` files.
- The finished app must launch without backend access and show an intentional offline
  state, not a blank or crashing screen.

Build the customer experience end-to-end first, then add employee tools. At every
phase, run analysis and tests before continuing. If the backend lacks an endpoint,
document the gap instead of fabricating a client-side substitute.
