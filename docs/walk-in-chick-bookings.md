# Walk-in chick bookings

Branch Managers and Customer Service open Chick Orders → New walk-in chick booking. Managers use assigned branches; Customer Service uses the existing cross-branch policy. Select an open chick offering and saved walk-in customer, or enter a new customer name and optional phone. Customers are shared with walk-in product sales.

The backend reuses configured chick prices, booking windows, and deposit percentages. Staff record external cash/card/mobile-money/bank-transfer payment from the minimum deposit through the full total. A booking with no deposit requirement may be recorded with zero payment. Duplicate request IDs return the original result and cannot create duplicate bookings. New non-cash payments require a reference. Each booking snapshots its customer details and records the staff member.

Staff can reopen the booking from Chick Orders to print an A4/58mm/80mm invoice or record the full outstanding balance. Reprints use server copy numbering and watermarks. Walk-in bookings must be fully paid before collection. Notifications are not sent to a fictitious app user; walk-in customer user_id is null.

Deploy V26__walk_in_chick_bookings.sql with the controller and staff-query updates. Backend integration tests cover deposits, rollback, retries, customerless-user bookings, permissions, balance payments and reprints. Mobile tests cover entry and remaining balance display.
