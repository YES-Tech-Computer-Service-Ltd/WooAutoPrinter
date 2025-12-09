---
description: Ensure Active (DB) and History (API) order flows stay in sync.
globs: "app/src/main/java/com/example/wooauto/**/*.kt"
---

# Order Data Source Consistency

## Background
- **History flow** (`OrdersViewModel.orders`) reads fresh API responses and converts them via `OrderDto.toOrder()`, so `woofoodInfo`, fee lines, and item options remain intact.
- **Active flow** (`OrdersActivePlaceholderScreen`) reads Room entities through `OrderMapper.mapEntityToDomain()`. If the entity schema omits fields, the Active UI becomes “thinner” than History.

## Rules
1. **Persist all WooFood metadata in the DB.** Every remote field (`deliveryDate`, `deliveryTime`, `orderMethod`, `isDelivery`, `deliveryFee`, `tip`, etc.) must be stored in `OrderEntity/WooFoodInfoEntity` and mapped back when building the domain model.
2. **Retain item-level options.** `OrderLineItemEntity` must always include option/add-on metadata so Active order details match the History view.
3. **No guessing from `customerNote`.** Only parse WooFood data through `WooFoodMetadataParser`. If the API did not supply a date/time, show an explicit placeholder in the UI instead of inferring it.
4. **Verify both flows for any change.** Whenever modifying mappers, repositories, or order UI, confirm that both Active (DB-backed) and History (API-backed) screens display identical information.

