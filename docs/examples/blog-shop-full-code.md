# Blog Shop Complete Application

## Modules

- User
- Product
- Order
- Payment
- Inventory

## Architecture

Controller -> Service -> Repository -> Database

## Example

```norm
@Post(path="/orders")
OrderResponse create(OrderRequest request) {
    return orderService.create(request)
}
```
