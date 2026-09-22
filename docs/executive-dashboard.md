# CEO dashboard and staff discounts

CEO users open the dashboard after employee login. Main Managers can open it using the chart icon in their toolbar. A Main Manager can create an employee with role CEO through Users; no account is automatically promoted.

Select a month, currency, and Whole company or a branch. Monthly targets are independent for the company and each branch, and are stored per currency. Set monthly target updates the selected scope only and records the staff member in the audit trail. Expected sales to date use elapsed calendar days in Africa/Harare; future months start at zero and completed months use the full target. An absent target is shown as Not set, not zero.

Net sales include paid/collected merchandise orders and confirmed/collected chick booking value, dated when the order was created. Merchandise discounts reduce net sales. Cash received sums paid payment records dated when received, so chick deposits are not double counted in sales. Cancelled/pending bookings are excluded from net sales and remain identifiable in the chick-status summary. Product rankings use quantities per SKU/pack size, with zero-sale published products included among the least sold. Inventory is current, regardless of selected month; available stock is on-hand minus reserved and low stock follows the branch/product threshold.

Branch Managers and Customer Service can enable Apply a discount on a walk-in sale, choose percentage or fixed amount, and enter a required reason. The backend calculates the discount, validates it against the subtotal, requires payment of the resulting total, and stores type, value, amount, reason, and staff ID. The subtotal, discount and reason print on A4 and thermal invoices, including reprints. Existing app checkout orders are unchanged.

Deploy V26 (walk-in chick bookings) and V27 (targets and discounts) with the backend before using these features in the updated app. V27 does not create a CEO login or alter existing user roles. Tests cover scoped access, currency separation, independent targets, percentage/fixed discounts, missing reasons, over-discount rejection and retry safety.
