# Feed calculator API

The demo calculator is public so the mobile app can load it without requiring a token.

## List calculator profiles

`GET /api/feed-calculator/profiles`

Returns the active animal profiles, their default planning period, source label and demo disclaimer.

## Calculate feed

`POST /api/feed-calculator/calculate`

```json
{
  "profileCode": "LAYER",
  "animalCount": 100,
  "days": 30,
  "bagSizeKg": 50
}
```

`days` and `bagSizeKg` are optional. The profile default and 50 kg are used when omitted.

Example result totals for this request are 1,055 kg across all rearing and laying phases,
21.10 exact bags and 22 whole bags to purchase. Each phase includes its matching catalogue SKU,
product name, kilograms, exact bags and rounded purchase bags.

All seeded rates in `V8__feed_calculator_demo.sql` are demonstration assumptions and must be
confirmed by Hyperfeeds' animal nutrition team before production use.
