# Walk-in sales

Branch Managers and Customer Service can open **Orders → New walk-in sale**.

1. Select the shop branch. Branch Managers can use only their assigned active collection branches; Customer Service retains its existing cross-branch access.
2. Search by customer name or phone and select a returning customer. For a first-time customer, choose **First-time customer**, enter their name and optionally their phone number. The customer is saved with the successful sale and can be selected next time. No customer login, OTP, email or smartphone is required.
3. Search the branch catalogue, enter quantities, and review the selected items and total.
4. Receive payment through the shop's usual cash, card/POS, mobile money or bank transfer process. Enter the amount applied to the sale and the external reference (required for non-cash payments), then confirm payment was received.
5. Select **Record sale and payment**, then **Print invoice**. Choose 80 mm receipt (default), 58 mm receipt, or A4 invoice. The app remembers the chosen paper on this device. Select Print to use the system print dialog, or Save / share PDF to open the invoice on the desktop connected to the printer.
6. Find the saved order in Orders to reprint its invoice or mark the goods collected.

Walk-in customer records are separate from app login accounts. Names are not unique; staff should use the phone number to distinguish customers where available. An existing exact phone number, ignoring whitespace, requires staff to select the saved customer instead of creating a duplicate. Invoice customer details are snapshotted at the time of sale.

The first version records full payment in a single currency. It supports the product catalogue (feed, medicine and other catalogue items), with chick bookings continuing through their existing workflow. A sale is marked paid; collection is recorded separately. No payment gateway is called by this workflow.

## Data and API

Deploy backend migration `V24__walk_in_sales.sql` before releasing the updated app. It adds walk-in customer records, customer snapshots and staff/payment audit fields. Existing app orders continue to require a user account through a database constraint.

- `GET /commerce/walk-in-sales/branches`: branches available to the signed-in member of staff.
- `GET /commerce/walk-in-sales/customers?q=...`: search saved walk-in customers by name or phone (minimum two characters, up to 30 results).
- `POST /commerce/walk-in-sales`: create the customer when necessary, record the paid order/payment/audit event and deduct available stock in one transaction. The request includes `requestId`, `branchId`, optional `customerId`, `customerName`, optional `customerPhone`, `items` (`productId`, `quantity`), `paymentMethod`, `paymentReference`, `amountPaid`, and `currency`.
- `GET /commerce/walk-in-sales/{id}/invoice`: retrieve the recorded sale for printing/reprinting.

Prices and totals are calculated on the server. The submitted amount/currency must match them. Stock updates cannot consume stock reserved for other orders. Failed requests roll back all changes. A repeated request ID with the same staff member and payload returns the original sale; changed payloads are rejected. The app securely retains an unresolved request across restarts so it can retry the same sale without another payment.

## Verification

Run backend tests with `./mvnw test`. For the PostgreSQL integration cases, set `WALK_IN_TEST_DB_URL` to an isolated empty test database accessible using the current operating-system username, then run the same command. The integration test migrates and seeds that database; never point it at production.

Run mobile tests with `flutter test`, then `flutter analyze` and `flutter build apk --debug`. `test/walk_in_sale_test.dart` covers new customers, regulars, retry behaviour and multi-page PDF generation. Generated invoice samples are written to `build/invoice-qa/`.

The implementation has been tested with an isolated PostgreSQL database and Flutter tests. Targeted Flutter analysis passes. Android APK build verification was blocked by repeated Gradle dependency download failures (connection resets/timeouts from Google Maven and Maven Central). Physical printer output still needs verification with the shop's printer. The feature is not deployed by these source changes.

## Desktop-connected receipt printers

Install the printer using its manufacturer’s desktop driver. Choose the matching 80 mm or 58 mm roll paper in both the app and the desktop print dialog, and print at actual size (100%). The PDF exports use the selected roll width with a content-sized length; driver printing respects the paper size returned by the system and paginates long sales. If the driver cannot use the exported custom length, print from the app with the driver’s supported paper length.

A USB printer connected only to a desktop is accessed from that desktop; a mobile phone cannot automatically print to it. Use Save / share PDF and open the file on the desktop, or run the app on a supported desktop platform with the printer installed. The current integration uses installed printer drivers, not raw ESC/POS or Bluetooth commands. Cutter and cash-drawer controls are not included. Physical printer output and a packaged desktop build have not been verified.

Paper preferences and receipt layouts are covered by `test/receipt_invoice_test.dart`. No additional dependencies were added for this enhancement.

## Original invoices and reprints

Deploy migration `V25__invoice_copy_tracking.sql` with the updated app. After the user chooses Print or Save / share PDF, the app calls `POST /commerce/walk-in-sales/{id}/invoice-copies`. The server atomically numbers issued copies and audits the staff member. Copy 1 is the original; subsequent copies carry a REPRINT watermark, label, copy number and UTC issue timestamp in all paper formats. Viewing the order or cancelling the paper selection does not consume a copy.

A copy is issued before handing the document to the operating-system print/share dialog. Cancelling that dialog or a printer/network failure may therefore consume a copy; the next app-generated copy is conservatively marked REPRINT. The system cannot prove paper delivery, or track repeated printing of a PDF already exported to the desktop. Such files retain their original markings. The copy count is shared across devices and is not reset by app reinstalls. Existing orders start at zero because the previous version did not track issued copies.
